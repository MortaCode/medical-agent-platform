package com.medagent.memory;

import com.medagent.common.JsonUtils;
import com.medagent.config.MedicalProperties;
import com.medagent.prompt.PromptTemplates;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 医学关键事实抽取器（跨话题常驻信息）。
 *
 * <p>在历史被归档时顺带从历史中抽取过敏史 / 慢病史 / 在服药物等长期有效的信息，
 * 之后无论用户切换到什么话题都会随每次请求注入，保证用药安全信息不因话题切换而丢失。</p>
 *
 * <p>合并策略见 {@link MedicalKeyFacts#mergeSafetyFirst}：安全信息只增不减。
 * 抽取失败时保留原有事实，不做任何降级清空。</p>
 */
@Component
@Slf4j
public class KeyFactExtractor {

    /** 单次抽取输入的历史字符上限。 */
    private static final int INPUT_CHAR_LIMIT = 4000;

    private final Map<String, MedicalKeyFacts> store = new ConcurrentHashMap<>();

    private final ChatClient chatClient;
    private final MedicalProperties properties;

    public KeyFactExtractor(ChatClient chatClient, MedicalProperties properties) {
        this.chatClient = chatClient;
        this.properties = properties;
    }

    /** 读取某会话已积累的关键事实（无则返回空事实对象）。 */
    public MedicalKeyFacts get(String conversationId) {
        return store.getOrDefault(conversationId, new MedicalKeyFacts());
    }

    public void remove(String conversationId) {
        store.remove(conversationId);
    }

    /**
     * 从一批历史消息中增量抽取并合并关键事实。
     *
     * <p>该方法为"尽力而为"：任何异常都只告警，不影响主链路，也不清空已有事实。</p>
     */
    public void update(String conversationId, List<MessageRecord> messages) {
        if (!properties.getMemory().isKeyFactsEnabled() || messages == null || messages.isEmpty()) {
            return;
        }
        MedicalKeyFacts existing = store.getOrDefault(conversationId, new MedicalKeyFacts());
        try {
            String prompt = String.format(PromptTemplates.KEY_FACT_EXTRACT,
                    JsonUtils.toJson(existing), renderHistory(messages));
            String json = chatClient.prompt().user(prompt).call().content();
            MedicalKeyFacts extracted = JsonUtils.fromJson(JsonUtils.cleanJson(json), MedicalKeyFacts.class);
            MedicalKeyFacts merged = MedicalKeyFacts.mergeSafetyFirst(existing, extracted);
            store.put(conversationId, merged);
            if (!merged.isEmpty()) {
                log.info("[MEMORY] 会话 " + conversationId + " 关键事实已更新："
                        + "过敏=" + merged.getAllergies()
                        + ", 慢病=" + merged.getChronicConditions()
                        + ", 在服=" + merged.getCurrentMedications());
            }
        } catch (Exception e) {
            log.warn("[MEMORY] 关键事实抽取失败，保留原有事实：{}", e.getMessage());
        }
    }

    private String renderHistory(List<MessageRecord> messages) {
        StringBuilder sb = new StringBuilder();
        for (MessageRecord record : messages) {
            String content = record.getContent();
            if (content == null || content.isBlank()) {
                continue;
            }
            if (sb.length() + content.length() > INPUT_CHAR_LIMIT) {
                break;
            }
            sb.append(record.getRole()).append(": ").append(content.trim()).append('\n');
        }
        return sb.toString();
    }
}
