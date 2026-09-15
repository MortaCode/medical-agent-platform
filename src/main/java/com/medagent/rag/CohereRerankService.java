package com.medagent.rag;

import com.fasterxml.jackson.databind.JsonNode;
import com.medagent.common.JsonUtils;
import com.medagent.config.MedicalProperties;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;

/**
 * Cohere Rerank 精排实现（云端交叉编码器）。
 *
 * <p>调用 Cohere /v1/rerank 对候选重排。若 API 不可用（无 key / 网络），自动降级为
 * 本地语义重排，保证链路可用。</p>
 */
public class CohereRerankService implements RerankService {

    private final RestClient restClient;
    private final MedicalProperties properties;
    private final LocalCrossEncoderRerankService fallback;

    public CohereRerankService(RestClient.Builder restClientBuilder,
                               EmbeddingModel embeddingModel,
                               MedicalProperties properties) {
        this.restClient = restClientBuilder.build();
        this.properties = properties;
        this.fallback = new LocalCrossEncoderRerankService(embeddingModel);
    }

    @Override
    public List<RetrievalCandidate> rerank(String query, List<RetrievalCandidate> candidates, int topK) {
        try {
            String apiKey = properties.getRag().getRerank().getCohere().getApiKey();
            if (apiKey == null || apiKey.isBlank()) {
                return fallback.rerank(query, candidates, topK);
            }
            List<String> docs = new ArrayList<>(candidates.size());
            for (RetrievalCandidate c : candidates) {
                docs.add(c.getContent());
            }
            String body = JsonUtils.toJson(java.util.Map.of(
                    "model", properties.getRag().getRerank().getCohere().getModel(),
                    "query", query,
                    "documents", docs,
                    "top_n", topK));

            String resp = restClient.post()
                    .uri("https://api.cohere.ai/v1/rerank")
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .body(body)
                    .retrieve()
                    .body(String.class);

            JsonNode root = JsonUtils.mapper().readTree(resp);
            JsonNode results = root.get("results");
            List<RetrievalCandidate> ranked = new ArrayList<>();
            for (JsonNode r : results) {
                int idx = r.get("index").asInt();
                double score = r.get("relevance_score").asDouble();
                RetrievalCandidate c = candidates.get(idx);
                c.setScore(score);
                ranked.add(c);
            }
            return ranked;
        } catch (Exception e) {
            // 降级到本地重排
            return fallback.rerank(query, candidates, topK);
        }
    }
}
