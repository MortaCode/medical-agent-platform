package com.medagent.memory;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 内存版话题段存储（演示 / 单实例）。
 *
 * <p>生产建议替换为 Redis 实现；注意向量字段需要序列化策略（如 float[] 转 Base64 或
 * 直接存入向量库）。</p>
 */
@Component
public class InMemoryTopicSegmentStore implements TopicSegmentStore {

    private final Map<String, List<TopicSegment>> store = new ConcurrentHashMap<>();

    @Override
    public List<TopicSegment> load(String conversationId) {
        return List.copyOf(store.getOrDefault(conversationId, List.of()));
    }

    @Override
    public void save(String conversationId, List<TopicSegment> segments) {
        store.put(conversationId, new CopyOnWriteArrayList<>(segments == null ? List.of() : segments));
    }

    @Override
    public void append(String conversationId, List<TopicSegment> segments, int maxRetain) {
        if (segments == null || segments.isEmpty()) {
            return;
        }
        store.compute(conversationId, (key, existing) -> {
            List<TopicSegment> merged = new CopyOnWriteArrayList<>(
                    existing == null ? List.of() : existing);
            merged.addAll(segments);
            // 只保留最新的 maxRetain 段，防止段库无限增长
            if (maxRetain > 0 && merged.size() > maxRetain) {
                return new CopyOnWriteArrayList<>(
                        merged.subList(merged.size() - maxRetain, merged.size()));
            }
            return merged;
        });
    }

    @Override
    public TopicSegment touch(String conversationId, String segmentId) {
        List<TopicSegment> segments = store.get(conversationId);
        if (segments == null) {
            return null;
        }
        for (TopicSegment s : segments) {
            if (s.getId().equals(segmentId)) {
                s.setHitCount(s.getHitCount() + 1);
                s.setLastAccessAt(System.currentTimeMillis());
                return s;
            }
        }
        return null;
    }

    @Override
    public void remove(String conversationId) {
        store.remove(conversationId);
    }
}
