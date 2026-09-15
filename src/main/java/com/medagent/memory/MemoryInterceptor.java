package com.medagent.memory;

import com.medagent.common.ConversationContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.UUID;

/**
 * Spring MVC 拦截器：从请求头或会话中提取 conversationId 注入 {@link ConversationContext}，
 * 供下游记忆 / 多智能体 / CoT 服务读取，实现「长期记忆」按会话隔离。
 */
@Component
public class MemoryInterceptor implements HandlerInterceptor {

    /** 请求头约定：X-Conversation-Id；缺省回退到 HttpSession。 */
    public static final String HEADER_CONVERSATION_ID = "X-Conversation-Id";
    public static final String SESSION_CONVERSATION_ID = "conversationId";

    @Override
    public boolean preHandle(@NonNull HttpServletRequest request,
                             @NonNull HttpServletResponse response,
                             @NonNull Object handler) {
        String conversationId = request.getHeader(HEADER_CONVERSATION_ID);
        if (conversationId == null || conversationId.isBlank()) {
            conversationId = (String) request.getSession().getAttribute(SESSION_CONVERSATION_ID);
        }
        if (conversationId == null || conversationId.isBlank()) {
            conversationId = UUID.randomUUID().toString();
            request.getSession().setAttribute(SESSION_CONVERSATION_ID, conversationId);
        }
        // 回写响应头，方便客户端在后续请求中持续携带同一会话
        response.setHeader(HEADER_CONVERSATION_ID, conversationId);
        ConversationContext.setConversationId(conversationId);
        return true;
    }

    @Override
    public void afterCompletion(@NonNull HttpServletRequest request,
                                @NonNull HttpServletResponse response,
                                @NonNull Object handler,
                                Exception ex) {
        ConversationContext.clear();
    }
}
