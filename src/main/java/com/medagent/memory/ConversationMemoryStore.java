package com.medagent.memory;

import java.util.List;

/**
 * 会话记忆持久化存储抽象。
 *
 * <p>默认提供内存实现 {@link InMemoryConversationMemoryStore}（演示/单实例）。
 * 生产可替换为 Redis / JDBC 实现以满足多实例共享与持久化。</p>
 */
public interface ConversationMemoryStore {

    void save(String conversationId, List<MessageRecord> records);

    List<MessageRecord> load(String conversationId);

    void remove(String conversationId);

    boolean exists(String conversationId);
}
