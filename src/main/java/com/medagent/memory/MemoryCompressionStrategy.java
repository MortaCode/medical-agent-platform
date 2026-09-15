package com.medagent.memory;

import com.medagent.common.TokenCounter;
import com.medagent.config.MedicalProperties;
import com.medagent.prompt.PromptTemplates;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 长期记忆压缩策略。
 *
 * <p>当会话历史 Token 超过阈值（默认 4000），调用大模型将「较早的历史」摘要压缩为一条
 * SystemMessage，并保留最近 N 条原始消息不压缩；压缩结果持久化回 {@link PersistentChatMemory}。
 * 目标：单次请求注入的 Token 降低 35%~40%。</p>
 */
@Component
public class MemoryCompressionStrategy {

    private final ChatClient chatClient;
    private final TokenCounter tokenCounter;
    private final PersistentChatMemory chatMemory;
    private final MedicalProperties properties;

    public MemoryCompressionStrategy(ChatClient chatClient,
                                     TokenCounter tokenCounter,
                                     PersistentChatMemory chatMemory,
                                     MedicalProperties properties) {
        this.chatClient = chatClient;
        this.tokenCounter = tokenCounter;
        this.chatMemory = chatMemory;
        this.properties = properties;
    }

    public record CompressionResult(boolean compressed, int beforeTokens, int afterTokens,
                                    double reductionRatio, String summary) {
        public static CompressionResult notNeeded(int tokens) {
            return new CompressionResult(false, tokens, tokens, 0.0, null);
        }
    }

    /**
     * 若超阈值则压缩并持久化，返回压缩结果。
     */
    public CompressionResult compressIfNeeded(String conversationId) {
        List<MessageRecord> all = chatMemory.getAllRecords(conversationId);
        int before = tokenCounter.count(
                all.stream().map(MessageRecord::getContent).filter(java.util.Objects::nonNull).toList());

        int threshold = properties.getMemory().getCompressionTokenThreshold();
        if (before <= threshold || all.isEmpty()) {
            return CompressionResult.notNeeded(before);
        }

        int keep = properties.getMemory().getRecentMessagesKeep();
        int split = Math.max(0, all.size() - keep);
        List<MessageRecord> older = all.subList(0, split);
        List<MessageRecord> recent = all.subList(split, all.size());

        String historyText = buildHistoryText(older);
        String summary = summarize(historyText);

        List<Message> compressed = new ArrayList<>();
        compressed.add(new SystemMessage("[历史对话压缩摘要]\n" + summary));
        compressed.addAll(chatMemory.toMessages(recent));

        chatMemory.replaceAll(conversationId, compressed);

        int after = tokenCounter.count(compressed.stream()
                .map(PersistentChatMemory::contentOf).toList());
        double ratio = tokenCounter.ratio(before, after);
        return new CompressionResult(true, before, after, ratio, summary);
    }

    private String buildHistoryText(List<MessageRecord> records) {
        StringBuilder sb = new StringBuilder();
        for (MessageRecord r : records) {
            sb.append(r.getRole()).append(": ")
              .append(r.getContent() == null ? "" : r.getContent()).append("\n");
        }
        return sb.toString();
    }

    private String summarize(String historyText) {
        String prompt = String.format(PromptTemplates.MEMORY_COMPRESS, historyText);
        return chatClient.prompt().user(prompt).call().content();
    }
}
