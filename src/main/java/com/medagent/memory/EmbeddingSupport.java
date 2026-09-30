package com.medagent.memory;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 向量计算支持：文本向量化（带 LRU 缓存）+ 余弦相似度。
 *
 * <p>话题分段与相关性召回都依赖它。所有方法在 embedding 不可用（无 key / 网络异常 /
 * 模型不支持）时返回 {@code null} 或 0，由调用方降级处理，<b>绝不因 embedding 故障中断主链路</b>。</p>
 */
@Component
@Slf4j
public class EmbeddingSupport {

    /** 缓存容量：按访问顺序淘汰。历史消息会被反复参与分段，缓存可显著减少调用。 */
    private static final int CACHE_CAPACITY = 2048;

    private final EmbeddingModel embeddingModel;

    private final Map<String, float[]> cache = Collections.synchronizedMap(
            new LinkedHashMap<>(256, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, float[]> eldest) {
                    return size() > CACHE_CAPACITY;
                }
            });

    public EmbeddingSupport(EmbeddingModel embeddingModel) {
        this.embeddingModel = embeddingModel;
    }

    /**
     * 计算文本向量。
     *
     * @return 向量；文本为空或计算失败时返回 {@code null}
     */
    public float[] embed(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        float[] cached = cache.get(text);
        if (cached != null) {
            return cached;
        }
        try {
            EmbeddingResponse response = embeddingModel.embedForResponse(List.of(text));
            float[] vector = response.getResult().getOutput();
            if (vector != null && vector.length > 0) {
                cache.put(text, vector);
                return vector;
            }
            return null;
        } catch (Exception e) {
            log.warn("[MEMORY] Embedding 计算失败，本次降级处理：{}", e.getMessage());
            return null;
        }
    }

    /**
     * 余弦相似度。
     *
     * @return 相似度（-1~1）；任一向量为空时返回 0
     */
    public double cosine(float[] a, float[] b) {
        if (a == null || b == null || a.length == 0 || a.length != b.length) {
            return 0d;
        }
        double dot = 0d;
        double normA = 0d;
        double normB = 0d;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        double denom = Math.sqrt(normA) * Math.sqrt(normB);
        return denom == 0d ? 0d : dot / denom;
    }

    /**
     * 相邻消息相似度判定，两个向量都可计算时才有效。
     *
     * @return 相似度；无法计算时返回 {@code -1}（调用方应视为"无法判断"，保守保持同段）
     */
    public double similarityOrUnknown(float[] a, float[] b) {
        if (a == null || b == null) {
            return -1d;
        }
        return cosine(a, b);
    }
}
