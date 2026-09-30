package com.medagent.memory;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.Message;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 记忆服务门面：对业务层暴露「构建本轮上下文」与「追加记忆」能力。
 *
 * <p>注意：本服务不依赖 ThreadLocal；conversationId 由调用方显式传入（在控制器请求线程中
 * 从 {@code ConversationContext} 捕获后向下传递），以保证在异步 SSE 编排中会话隔离依然正确。</p>
 */
@Service
@Slf4j
public class MemoryService {

    private final PersistentChatMemory chatMemory;
    private final MemoryCompressionStrategy compressionStrategy;
    private final RelevantMemorySelector relevantMemorySelector;
    private final TopicSegmentStore topicSegmentStore;
    private final KeyFactExtractor keyFactExtractor;

    public MemoryService(PersistentChatMemory chatMemory,
                         MemoryCompressionStrategy compressionStrategy,
                         RelevantMemorySelector relevantMemorySelector,
                         TopicSegmentStore topicSegmentStore,
                         KeyFactExtractor keyFactExtractor) {
        this.chatMemory = chatMemory;
        this.compressionStrategy = compressionStrategy;
        this.relevantMemorySelector = relevantMemorySelector;
        this.topicSegmentStore = topicSegmentStore;
        this.keyFactExtractor = keyFactExtractor;
    }

    /**
     * 构建本轮请求的记忆上下文（<b>推荐入口</b>）。
     *
     * <p>与旧 {@link #loadContext} 的区别：不再"全量返回历史"。执行顺序为：</p>
     * <ol>
     *   <li>触发归档 —— 超阈值时把更早历史切成话题段并抽关键事实；</li>
     *   <li>取最近原文 —— 保证近距指代（"它""那个药"）可解析；</li>
     *   <li>取跨话题常驻的关键事实 —— 过敏史/慢病/在服药物，无条件注入；</li>
     *   <li>按相关性召回历史话题段 —— <b>与当前问题无关的历史不会进入上下文</b>。</li>
     * </ol>
     *
     * @param conversationId 会话 ID
     * @param query          当前用户问题（用于相关性判定）
     * @return 本轮上下文；conversationId 为空时返回空上下文
     */
    public MemoryContext buildContext(String conversationId, String query) {
        if (conversationId == null || conversationId.isBlank()) {
            return MemoryContext.empty();
        }
        compressionStrategy.compressIfNeeded(conversationId);

        List<MessageRecord> recent = chatMemory.getAllRecords(conversationId);
        MedicalKeyFacts facts = keyFactExtractor.get(conversationId);
        List<TopicSegment> segments = topicSegmentStore.load(conversationId);

        RelevantMemorySelector.SelectionResult selection =
                relevantMemorySelector.select(conversationId, query, segments);

        MemoryContext context = new MemoryContext(
                facts,
                selection.segments(),
                recent,
                selection.topicShift(),
                selection.degraded(),
                selection.topScore());
        log.info("[MEMORY] 会话 " + conversationId + " 本轮上下文： " + context.describe());
        return context;
    }

    /**
     * 兼容旧用法：加载完整历史消息列表。
     *
     * <p>缺少相关性过滤，会让无关话题的历史一并进入上下文，新代码请改用
     * {@link #buildContext(String, String)}。</p>
     */
    @Deprecated
    public List<Message> loadContext(String conversationId) {
        compressionStrategy.compressIfNeeded(conversationId);
        return chatMemory.getAll(conversationId);
    }

    /** 显式归档（返回统计，便于可观测）。 */
    public MemoryCompressionStrategy.CompressionResult compress(String conversationId) {
        return compressionStrategy.compressIfNeeded(conversationId);
    }

    /** 追加一条或多条消息到会话记忆。 */
    public void remember(String conversationId, Message... messages) {
        chatMemory.add(conversationId, List.of(messages));
    }

    /** 清空会话记忆（含话题段与关键事实）。 */
    public void clear(String conversationId) {
        chatMemory.clear(conversationId);
        topicSegmentStore.remove(conversationId);
        keyFactExtractor.remove(conversationId);
    }
}
