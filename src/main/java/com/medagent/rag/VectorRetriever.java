package com.medagent.rag;

import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 阶段一粗筛 · Embedding 向量检索（Milvus）。
 *
 * <p>依赖 spring-ai-starter-vector-store-milvus 自动配置的 {@link VectorStore}，
 * 默认相似度为 COSINE，集合/维度见 application.yml。</p>
 */
@Component
public class VectorRetriever {

    private final VectorStore vectorStore;

    public VectorRetriever(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    /** 向量 TopK 检索。 */
    public List<RetrievalCandidate> retrieve(String query, int topK) {
        SearchRequest request = SearchRequest.builder()
                .query(query)
                .topK(topK)
                .build();
        List<Document> docs = vectorStore.similaritySearch(request);
        List<RetrievalCandidate> result = new ArrayList<>();
        for (Document d : docs) {
            Map<String, Object> meta = d.getMetadata();
            result.add(new RetrievalCandidate(
                    d.getId(),
                    d.getText(),
                    str(meta.get("source")),
                    str(meta.get("page")),
                    str(meta.get("url")),
                    RetrievalCandidate.SourceType.VECTOR,
                    d.getScore()));
        }
        return result;
    }

    /** 批量写入 Milvus（摄入阶段调用）。 */
    public void addAll(List<MedicalChunk> chunks) {
        List<Document> docs = new ArrayList<>();
        for (MedicalChunk c : chunks) {
            Map<String, Object> meta = Map.of(
                    "source", c.getSource() == null ? "" : c.getSource(),
                    "page", c.getPage() == null ? "" : c.getPage(),
                    "url", c.getUrl() == null ? "" : c.getUrl());
            docs.add(new Document(c.getChunkId(), c.getContent(), meta));
        }
        vectorStore.add(docs);
    }

    private String str(Object o) {
        return o == null ? "" : String.valueOf(o);
    }
}
