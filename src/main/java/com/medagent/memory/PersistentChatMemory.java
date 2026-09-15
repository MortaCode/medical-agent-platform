package com.medagent.memory;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 持久化会话记忆（按 conversationId 隔离）。
 *
 * <p>为避免不同 Spring AI 小版本 {@code ChatMemory} 接口签名漂移（get(String) / get(String,int)），
 * 此处不实现该接口，而是提供稳定的自有 API，底层通过 {@link ConversationMemoryStore} 落地，
 * 支持压缩摘要持久化。</p>
 */
@Component
public class PersistentChatMemory {

    private final ConversationMemoryStore store;

    public PersistentChatMemory(ConversationMemoryStore store) {
        this.store = store;
    }

    /** 追加消息到会话（转换为可持久化记录）。 */
    public void add(String conversationId, List<Message> messages) {
        List<MessageRecord> existing = new ArrayList<>(store.load(conversationId));
        for (Message m : messages) {
            existing.add(toRecord(m));
        }
        store.save(conversationId, existing);
    }

    /** 返回完整历史（重构为 Spring AI Message，供需要原生消息的组件使用）。 */
    public List<Message> getAll(String conversationId) {
        return toMessages(store.load(conversationId));
    }

    /** 返回原始记录（压缩/计数时优先使用，避免 Message 接口无 getContent 的限制）。 */
    public List<MessageRecord> getAllRecords(String conversationId) {
        return store.load(conversationId);
    }

    public void clear(String conversationId) {
        store.remove(conversationId);
    }

    /** 用压缩后的消息集整体替换该会话历史（压缩后持久化）。 */
    public void replaceAll(String conversationId, List<Message> compressed) {
        store.save(conversationId, compressed.stream().map(this::toRecord).toList());
    }

    /** 将消息列表重构为 Spring AI Message（重建时丢弃元数据，仅保留角色与内容）。 */
    public List<Message> toMessages(List<MessageRecord> records) {
        List<Message> messages = new ArrayList<>(records.size());
        for (MessageRecord r : records) {
            messages.add(toMessage(r));
        }
        return messages;
    }

    private MessageRecord toRecord(Message m) {
        return new MessageRecord(
                m.getMessageType().name(),
                contentOf(m),
                Map.of());
    }

    private Message toMessage(MessageRecord r) {
        String c = r.getContent() == null ? "" : r.getContent();
        return switch (r.getRole()) {
            case "USER" -> new UserMessage(c);
            case "ASSISTANT" -> new AssistantMessage(c);
            case "SYSTEM" -> new SystemMessage(c);
            default -> new AssistantMessage(c);
        };
    }

    /** 从任意 Message 提取文本内容（兼容各子类型；默认空串）。 */
    public static String contentOf(Message m) {
        if (m instanceof UserMessage u) {
            return u.getText();
        }
        if (m instanceof AssistantMessage a) {
            return a.getText();
        }
        if (m instanceof SystemMessage s) {
            return s.getText();
        }
        if (m instanceof ToolResponseMessage t) {
            return t.getText();
        }
        return "";
    }
}
