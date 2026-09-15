package com.medagent.rag;

import org.jsoup.Jsoup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 语料摄入（演示 12 万实体关系级知识库构建）。
 *
 * <p>启动时若配置了 {@code medical.rag.corpus-path}，则扫描该目录下 .txt/.md/.html，
 * 用 {@link DocumentChunker} 切片后，分别写入 Lucene(BM25) 与 Milvus(向量) 两个索引。
 * HTML 文献用 jsoup 抽取正文。未配置路径时自动跳过（不影响服务启动）。</p>
 */
@Configuration
public class RagDataInitializer {

    private static final Logger log = LoggerFactory.getLogger(RagDataInitializer.class);

    @Value("${medical.rag.corpus-path:}")
    private String corpusPath;

    @Bean
    public ApplicationRunner ingestCorpus(DocumentChunker chunker,
                                         Bm25Retriever bm25Retriever,
                                         VectorRetriever vectorRetriever) {
        return args -> {
            if (corpusPath == null || corpusPath.isBlank()) {
                log.info("[RAG] 未配置 corpus-path，跳过语料摄入（运行时可不依赖内置语料）。");
                return;
            }
            File root = new File(corpusPath);
            if (!root.exists() || !root.isDirectory()) {
                log.warn("[RAG] corpus-path 不存在或非目录：{}", corpusPath);
                return;
            }
            List<MedicalChunk> all = new ArrayList<>();
            PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            Resource[] resources = resolver.getResources("file:" + corpusPath + "/**/*.{txt,md,html,htm}");
            for (Resource res : resources) {
                try {
                    File f = res.getFile();
                    String text = extractText(f);
                    String source = f.getName();
                    List<MedicalChunk> chunks = chunker.chunk(
                            source, text, source, "1", "", Map.of("doc", source));
                    all.addAll(chunks);
                } catch (Exception e) {
                    log.warn("[RAG] 处理文件失败：{}", res.getFilename(), e);
                }
            }
            bm25Retriever.indexAll(all);
            vectorRetriever.addAll(all);
            log.info("[RAG] 语料摄入完成：文件 {} 个，切片 {} 个（BM25 + Milvus 双索引已建立）",
                    resources.length, all.size());
        };
    }

    private String extractText(File f) throws Exception {
        String name = f.getName().toLowerCase();
        if (name.endsWith(".html") || name.endsWith(".htm")) {
            return Jsoup.parse(f, "UTF-8").text(); // jsoup 抽取正文，去除标签
        }
        return Files.readString(f.toPath());
    }
}
