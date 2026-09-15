package com.medagent.memory;

import org.springframework.ai.chat.messages.Message;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 记忆服务门面：对业务层暴露「压缩加载」与「追加记忆」能力。
 *
 * <p>注意：本服务不依赖 ThreadLocal；conversationId 由调用方显式传入（在控制器请求线程中
 * 从 {@code ConversationContext} 捕获后向下传递），以保证在异步 SSE 编排中会话隔离依然正确。</p>
 */
@Service
public class MemoryService {

    private final PersistentChatMemory chatMemory;
    private final MemoryCompressionStrategy compressionStrategy;

    public MemoryService(PersistentChatMemory chatMemory,
                         MemoryCompressionStrategy compressionStrategy) {
        this.chatMemory = chatMemory;
        this.compressionStrategy = compressionStrategy;
    }

    /** 加载会话记忆：先按需压缩，返回用于本轮请求的完整消息列表。 */
    public List<Message> loadContext(String conversationId) {
        compressionStrategy.compressIfNeeded(conversationId);
        return chatMemory.getAll(conversationId);
    }

    /** 显式压缩（返回压缩统计，便于可观测）。 */
    public MemoryCompressionStrategy.CompressionResult compress(String conversationId) {
        return compressionStrategy.compressIfNeeded(conversationId);
    }

    /** 追加一条或多条消息到会话记忆。 */
    public void remember(String conversationId, Message... messages) {
        chatMemory.add(conversationId, List.of(messages));
    }

    public void clear(String conversationId) {
        chatMemory.clear(conversationId);
    }
}
