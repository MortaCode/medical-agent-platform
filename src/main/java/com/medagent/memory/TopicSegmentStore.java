package com.medagent.memory;

import java.util.List;

/**
 * 话题段存储抽象。
 *
 * <p>默认提供内存实现 {@link InMemoryTopicSegmentStore}；生产可替换为 Redis / JDBC 实现，
 * 以满足多实例共享与重启后历史不丢。</p>
 */
public interface TopicSegmentStore {

    List<TopicSegment> load(String conversationId);

    /** 整体覆盖。 */
    void save(String conversationId, List<TopicSegment> segments);

    /**
     * 追加新段并按上限裁剪（保留最新的 {@code maxRetain} 段）。
     *
     * <p>历史是按滚动归档的方式逐批切段的，因此这里用追加语义；裁剪防止段库无限增长。</p>
     */
    void append(String conversationId, List<TopicSegment> segments, int maxRetain);

    /** 记录段被召回命中（用于可观测量与淘汰策略）。 */
    TopicSegment touch(String conversationId, String segmentId);

    void remove(String conversationId);
}
