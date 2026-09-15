package com.medagent.config;

import com.medagent.rag.CohereRerankService;
import com.medagent.rag.LocalCrossEncoderRerankService;
import com.medagent.rag.RerankService;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * 根据 medical.rag.rerank.provider 选择精排实现：cohere（云端）/ local（本地语义重排）。
 */
@Configuration
public class RerankConfig {

    @Bean
    public RerankService rerankService(RestClient.Builder restClientBuilder,
                                       EmbeddingModel embeddingModel,
                                       MedicalProperties properties) {
        String provider = properties.getRag().getRerank().getProvider();
        if ("cohere".equalsIgnoreCase(provider)) {
            return new CohereRerankService(restClientBuilder, embeddingModel, properties);
        }
        return new LocalCrossEncoderRerankService(embeddingModel);
    }
}
