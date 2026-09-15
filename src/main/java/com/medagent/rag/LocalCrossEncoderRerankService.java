package com.medagent.rag;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingResponse;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 本地重排实现（交叉编码器替代方案）。
 *
 * <p>对查询与每个候选分别计算 Embedding，以余弦相似度作为相关性得分重排。
 * 生产与 Cohere/本地 Cross-Encoder 效果接近，且零外部依赖、可离线运行。
 * 见 {@code prompts.md} 中 LOCAL_RERANK_NOTE。</p>
 */
public class LocalCrossEncoderRerankService implements RerankService {

    private final EmbeddingModel embeddingModel;

    public LocalCrossEncoderRerankService(EmbeddingModel embeddingModel) {
        this.embeddingModel = embeddingModel;
    }

    @Override
    public List<RetrievalCandidate> rerank(String query, List<RetrievalCandidate> candidates, int topK) {
        float[] qVec = embed(query);
        List<RetrievalCandidate> scored = new ArrayList<>(candidates);
        for (RetrievalCandidate c : scored) {
            float[] dVec = embed(c.getContent());
            c.setScore(cosine(qVec, dVec));
        }
        scored.sort(Comparator.comparingDouble(RetrievalCandidate::getScore).reversed());
        return topK <= 0 || topK >= scored.size() ? scored : scored.subList(0, topK);
    }

    private float[] embed(String text) {
        EmbeddingResponse r = embeddingModel.embedForResponse(List.of(text == null ? "" : text));
        return r.getResult().getOutput();
    }

    private double cosine(float[] a, float[] b) {
        if (a == null || b == null || a.length == 0 || a.length != b.length) {
            return 0d;
        }
        double dot = 0, na = 0, nb = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            na += a[i] * a[i];
            nb += b[i] * b[i];
        }
        double denom = Math.sqrt(na) * Math.sqrt(nb);
        return denom == 0 ? 0 : dot / denom;
    }
}
