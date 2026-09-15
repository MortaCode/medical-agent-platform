package com.medagent.common;

/**
 * 请求级会话上下文（ThreadLocal）。
 *
 * <p>由 {@code MemoryInterceptor} 在 preHandle 中写入 conversationId，
 * 业务层（记忆 / 多智能体 / CoT 服务）随时读取；afterCompletion 清理。</p>
 */
public final class ConversationContext {

    private static final ThreadLocal<String> CONVERSATION_ID = new ThreadLocal<>();
    private static final ThreadLocal<String> USER_ID = new ThreadLocal<>();

    private ConversationContext() {
    }

    public static void setConversationId(String id) {
        CONVERSATION_ID.set(id);
    }

    public static String getConversationId() {
        return CONVERSATION_ID.get();
    }

    public static void setUserId(String id) {
        USER_ID.set(id);
    }

    public static String getUserId() {
        return USER_ID.get();
    }

    public static void clear() {
        CONVERSATION_ID.remove();
        USER_ID.remove();
    }
}
