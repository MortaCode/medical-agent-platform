package com.medagent.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAI 模型装配。
 *
 * <p>spring-ai-openai-spring-boot-starter 已自动配置 {@code ChatClient.Builder} 与
 * {@code EmbeddingModel}（请勿重复声明 EmbeddingModel bean，否则会导致按类型注入歧义）。
 * 此处仅封装一个默认 {@link ChatClient} 供各 Agent 注入；需要 reasoning_effort 的推理在
 * {@code ThinkService}/{@code DiagnosisAgent} 内通过 OpenAiChatOptions 逐次覆盖。</p>
 */
@Configuration
public class OpenAiConfig {

    @Bean
    public ChatClient chatClient(ChatClient.Builder builder) {
        return builder.build();
    }
}
