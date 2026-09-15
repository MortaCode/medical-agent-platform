package com.medagent.memory;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 内存版会话记忆存储（单实例演示用）。生产建议替换为 Redis/JDBC 实现。
 */
@Component
public class InMemoryConversationMemoryStore implements ConversationMemoryStore {

    private final Map<String, List<MessageRecord>> store = new ConcurrentHashMap<>();

    @Override
    public void save(String conversationId, List<MessageRecord> records) {
        // 替换为不可变副本，避免外部引用修改
        store.put(conversationId, List.copyOf(records));
    }

    @Override
    public List<MessageRecord> load(String conversationId) {
        return store.getOrDefault(conversationId, List.of());
    }

    @Override
    public void remove(String conversationId) {
        store.remove(conversationId);
    }

    @Override
    public boolean exists(String conversationId) {
        return store.containsKey(conversationId);
    }
}
