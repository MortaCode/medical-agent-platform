package com.medagent.rag;

import com.medagent.config.MedicalProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 阶段一粗筛 · 混合检索器：并行执行「向量 Top50」与「BM25 Top50」，
 * 合并去重后输出候选集（默认 100），供阶段二精排。
 */
@Component
public class HybridRetriever {

    private final VectorRetriever vectorRetriever;
    private final Bm25Retriever bm25Retriever;
    private final MedicalProperties properties;

    public HybridRetriever(VectorRetriever vectorRetriever, Bm25Retriever bm25Retriever,
                           MedicalProperties properties) {
        this.vectorRetriever = vectorRetriever;
        this.bm25Retriever = bm25Retriever;
        this.properties = properties;
    }

    public List<RetrievalCandidate> retrieve(String query) {
        int vectorTopK = properties.getRag().getVectorTopK();
        int bm25TopK = properties.getRag().getBm25TopK();
        int candidateK = properties.getRag().getHybridCandidateK();

        CompletableFuture<List<RetrievalCandidate>> fv =
                CompletableFuture.supplyAsync(() -> vectorRetriever.retrieve(query, vectorTopK));
        CompletableFuture<List<RetrievalCandidate>> fb =
                CompletableFuture.supplyAsync(() -> bm25Retriever.retrieve(query, bm25TopK));

        List<RetrievalCandidate> vector = fv.join();
        List<RetrievalCandidate> bm25 = fb.join();

        // 合并 + 去重（同一块来自两路的视为同一候选，保留最高分并标记双来源）
        Map<String, RetrievalCandidate> merged = new LinkedHashMap<>();
        accept(merged, vector, RetrievalCandidate.SourceType.VECTOR);
        accept(merged, bm25, RetrievalCandidate.SourceType.BM25);

        List<RetrievalCandidate> candidates = new ArrayList<>(merged.values());
        if (candidates.size() > candidateK) {
            candidates = candidates.subList(0, candidateK);
        }
        return candidates;
    }

    private void accept(Map<String, RetrievalCandidate> merged, List<RetrievalCandidate> list,
                        RetrievalCandidate.SourceType prefer) {
        for (RetrievalCandidate c : list) {
            RetrievalCandidate existing = merged.get(c.dedupKey());
            if (existing == null) {
                merged.put(c.dedupKey(), c);
            } else if (c.getScore() > existing.getScore()) {
                existing.setScore(c.getScore());
            }
        }
    }
}
