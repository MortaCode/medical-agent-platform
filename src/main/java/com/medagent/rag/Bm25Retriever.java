package com.medagent.rag;

import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.similarities.BM25Similarity;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.springframework.stereotype.Component;

import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * 阶段一粗筛 · Lucene BM25 关键词检索。
 *
 * <p>使用内存 {@link RAMDirectory} 构建倒排索引，评分采用 {@link BM25Similarity}。
 * 支持增量建库，覆盖 12 万级实体关系语料（生产可替换为 FSDirectory + 外部索引）。</p>
 */
@Component
public class Bm25Retriever {

    private final ByteBuffersDirectory directory = new ByteBuffersDirectory();
    private final StandardAnalyzer analyzer = new StandardAnalyzer();
    private IndexWriter writer;

    public Bm25Retriever() {
        IndexWriterConfig cfg = new IndexWriterConfig(analyzer);
        cfg.setSimilarity(new BM25Similarity());
        try {
            this.writer = new IndexWriter(directory, cfg);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to init BM25 index", e);
        }
    }

    /** 增量索引一个切片。 */
    public synchronized void add(MedicalChunk chunk) {
        Document doc = new Document();
        doc.add(new StringField("chunkId", chunk.getChunkId(), Field.Store.YES));
        doc.add(new TextField("content", chunk.getContent(), Field.Store.YES));
        doc.add(new StringField("source", chunk.getSource() == null ? "" : chunk.getSource(), Field.Store.YES));
        doc.add(new StringField("page", chunk.getPage() == null ? "" : chunk.getPage(), Field.Store.YES));
        doc.add(new StringField("url", chunk.getUrl() == null ? "" : chunk.getUrl(), Field.Store.YES));
        try {
            writer.addDocument(doc);
            writer.commit();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to index chunk " + chunk.getChunkId(), e);
        }
    }

    public void indexAll(List<MedicalChunk> chunks) {
        chunks.forEach(this::add);
    }

    /** BM25 TopK 检索。 */
    public List<RetrievalCandidate> retrieve(String query, int topK) {
        List<RetrievalCandidate> result = new ArrayList<>();
        try (DirectoryReader reader = DirectoryReader.open(directory)) {
            IndexSearcher searcher = new IndexSearcher(reader);
            searcher.setSimilarity(new BM25Similarity());
            QueryParser parser = new QueryParser("content", analyzer);
            org.apache.lucene.search.Query q = parser.parse(query);
            TopDocs top = searcher.search(q, topK);
            for (ScoreDoc sd : top.scoreDocs) {
                Document d = searcher.storedFields().document(sd.doc);
                result.add(new RetrievalCandidate(
                        d.get("chunkId"),
                        d.get("content"),
                        d.get("source"),
                        d.get("page"),
                        d.get("url"),
                        RetrievalCandidate.SourceType.BM25,
                        sd.score));
            }
        } catch (Exception e) {
            // 解析失败（特殊字符等）退化为空结果
            return result;
        }
        return result;
    }

    /** 已索引文档数（可观测）。 */
    public int indexedCount() {
        try (DirectoryReader reader = DirectoryReader.open(directory)) {
            return reader.numDocs();
        } catch (IOException e) {
            return 0;
        }
    }

    @PreDestroy
    public void close() throws IOException {
        writer.close();
        directory.close();
    }
}
