package com.medagent.memory;

import com.fasterxml.jackson.databind.JsonNode;
import com.medagent.common.JsonUtils;
import com.medagent.config.MedicalProperties;
import com.medagent.prompt.PromptTemplates;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 话题分段器：把一段连续的历史消息切成若干"语义连续"的话题段。
 *
 * <p>切分依据是相邻消息的向量余弦相似度：低于阈值即认为发生了话题切换，在此处断开。
 * 每段随后被独立摘要，成为可被单独召回的最小单位 —— 这是"无关历史不污染当前问题"的基础。</p>
 *
 * <p>摘要采用<b>批量单次调用</b>（一次请求处理多个段），避免段数多时放大 LLM 调用次数。</p>
 */
@Component
@Slf4j
public class TopicSegmenter {

    /** 单次摘要请求输入的历史字符上限，超出则分批调用。 */
    private static final int BATCH_CHAR_LIMIT = 6000;

    /** 摘要生成失败时，兜底摘要的最大字符数。 */
    private static final int FALLBACK_SUMMARY_LIMIT = 300;

    private final EmbeddingSupport embeddingSupport;
    private final ChatClient chatClient;
    private final MedicalProperties properties;

    public TopicSegmenter(EmbeddingSupport embeddingSupport,
                          ChatClient chatClient,
                          MedicalProperties properties) {
        this.embeddingSupport = embeddingSupport;
        this.chatClient = chatClient;
        this.properties = properties;
    }

    /**
     * 将历史消息切分为话题段。
     *
     * @param conversationId 会话 ID
     * @param messages       待归档的历史消息（时间序）
     * @return 话题段列表；输入为空时返回空列表
     */
    public List<TopicSegment> segment(String conversationId, List<MessageRecord> messages) {
        if (messages == null || messages.isEmpty()) {
            return List.of();
        }
        List<List<MessageRecord>> groups = splitByTopic(messages);
        log.info("[MEMORY] 会话 {} 的历史 {} 条消息被切分为 {} 个话题段",
                conversationId, messages.size(), groups.size());

        List<TopicSegment> segments = new ArrayList<>(groups.size());
        int cursor = 0;
        while (cursor < groups.size()) {
            List<List<MessageRecord>> batch = new ArrayList<>();
            int chars = 0;
            while (cursor < groups.size()) {
                int size = charCount(groups.get(cursor));
                if (!batch.isEmpty() && chars + size > BATCH_CHAR_LIMIT) {
                    break;
                }
                batch.add(groups.get(cursor));
                chars += size;
                cursor++;
            }
            List<SegmentDraft> drafts = summarize(batch);
            for (int i = 0; i < batch.size(); i++) {
                SegmentDraft draft = i < drafts.size() ? drafts.get(i) : null;
                segments.add(toSegment(conversationId, batch.get(i), draft));
            }
        }
        return segments;
    }

    /**
     * 以「对话轮」为单位切分话题。
     *
     * <p>不逐条比较相邻消息：同一轮内 USER 提问与 ASSISTANT 回答的内容天然不同，
     * 逐条比较会把一轮问答误切成两段。这里先把消息聚合为轮（USER 起始，直到下一个 USER），
     * 再用整轮文本计算向量并比较相邻轮，更贴近真实的话题切换点。
     * 相似度无法计算时（向量为空）保守地保持同段。</p>
     */
    private List<List<MessageRecord>> splitByTopic(List<MessageRecord> messages) {
        double boundary = properties.getMemory().getTopicBoundaryThreshold();
        List<List<MessageRecord>> turns = groupIntoTurns(messages);
        if (turns.isEmpty()) {
            return List.of();
        }

        List<float[]> vectors = new ArrayList<>(turns.size());
        for (List<MessageRecord> turn : turns) {
            vectors.add(embeddingSupport.embed(joinTurn(turn)));
        }

        List<List<MessageRecord>> groups = new ArrayList<>();
        List<MessageRecord> current = new ArrayList<>(turns.get(0));
        for (int i = 1; i < turns.size(); i++) {
            double similarity = embeddingSupport.similarityOrUnknown(vectors.get(i - 1), vectors.get(i));
            boolean topicChanged = similarity >= 0 && similarity < boundary;
            if (topicChanged) {
                groups.add(current);
                current = new ArrayList<>();
            }
            current.addAll(turns.get(i));
        }
        groups.add(current);
        return groups;
    }

    /** 按 USER 边界聚合为对话轮。 */
    private List<List<MessageRecord>> groupIntoTurns(List<MessageRecord> messages) {
        List<List<MessageRecord>> turns = new ArrayList<>();
        List<MessageRecord> current = new ArrayList<>();
        for (MessageRecord message : messages) {
            if ("USER".equals(message.getRole()) && !current.isEmpty()) {
                turns.add(current);
                current = new ArrayList<>();
            }
            current.add(message);
        }
        if (!current.isEmpty()) {
            turns.add(current);
        }
        return turns;
    }

    /** 拼接一轮内的全部文本作为该轮的语义代表。 */
    private String joinTurn(List<MessageRecord> turn) {
        StringBuilder sb = new StringBuilder();
        for (MessageRecord record : turn) {
            String content = record.getContent();
            if (content != null && !content.isBlank()) {
                sb.append(content.trim()).append('\n');
            }
        }
        return sb.toString();
    }

    /** 批量生成摘要；任一批次失败时该批次回退为原文截断摘要。 */
    private List<SegmentDraft> summarize(List<List<MessageRecord>> batch) {
        if (batch.isEmpty()) {
            return List.of();
        }
        try {
            String prompt = String.format(PromptTemplates.TOPIC_SEGMENT_SUMMARY, renderBatch(batch));
            String json = chatClient.prompt().user(prompt).call().content();
            List<SegmentDraft> drafts = parseDrafts(json);
            if (drafts.size() == batch.size()) {
                return drafts;
            }
            log.warn("[MEMORY] 话题摘要返回数量不匹配（期望 {}，实际 {}），按序对齐",
                    batch.size(), drafts.size());
            return drafts;
        } catch (Exception e) {
            log.warn("[MEMORY] 话题摘要生成失败，回退为原文截断：{}", e.getMessage());
            return List.of();
        }
    }

    private String renderBatch(List<List<MessageRecord>> batch) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < batch.size(); i++) {
            sb.append("【片段").append(i + 1).append("】\n");
            for (MessageRecord r : batch.get(i)) {
                String content = r.getContent() == null ? "" : r.getContent().trim();
                if (!content.isEmpty()) {
                    sb.append(r.getRole()).append(": ").append(content).append('\n');
                }
            }
            sb.append('\n');
        }
        return sb.toString();
    }

    private List<SegmentDraft> parseDrafts(String json) {
        List<SegmentDraft> drafts = new ArrayList<>();
        if (json == null || json.isBlank()) {
            return drafts;
        }
        try {
            JsonNode root = JsonUtils.mapper().readTree(JsonUtils.cleanJson(json));
            if (root == null || !root.isArray()) {
                return drafts;
            }
            for (JsonNode node : root) {
                SegmentDraft draft = new SegmentDraft();
                draft.summary = node.path("summary").asText("");
                List<String> keywords = new ArrayList<>();
                JsonNode kw = node.path("keywords");
                if (kw.isArray()) {
                    kw.forEach(k -> {
                        String text = k.asText("");
                        if (!text.isBlank()) {
                            keywords.add(text);
                        }
                    });
                }
                draft.keywords = keywords;
                drafts.add(draft);
            }
        } catch (Exception e) {
            log.warn("[MEMORY] 话题摘要 JSON 解析失败：{}", e.getMessage());
        }
        return drafts;
    }

    private TopicSegment toSegment(String conversationId, List<MessageRecord> group, SegmentDraft draft) {
        String summary = draft != null && draft.summary != null && !draft.summary.isBlank()
                ? draft.summary
                : fallbackSummary(group);

        TopicSegment segment = new TopicSegment();
        segment.setId(conversationId + "-S" + UUID.randomUUID().toString().substring(0, 8));
        segment.setConversationId(conversationId);
        segment.setSummary(summary);
        segment.setKeywords(draft != null && draft.keywords != null ? draft.keywords : List.of());
        segment.setEmbedding(embeddingSupport.embed(summary));
        segment.setMessageCount(group.size());
        long now = System.currentTimeMillis();
        segment.setCreatedAt(now);
        segment.setLastAccessAt(now);
        return segment;
    }

    private String fallbackSummary(List<MessageRecord> group) {
        StringBuilder sb = new StringBuilder();
        for (MessageRecord r : group) {
            String content = r.getContent() == null ? "" : r.getContent().trim();
            if (!content.isEmpty()) {
                sb.append(r.getRole()).append(": ").append(content).append('\n');
            }
        }
        String text = sb.toString().trim();
        return text.length() > FALLBACK_SUMMARY_LIMIT
                ? text.substring(0, FALLBACK_SUMMARY_LIMIT) + "…"
                : text;
    }

    private int charCount(List<MessageRecord> group) {
        int sum = 0;
        for (MessageRecord r : group) {
            sum += r.getContent() == null ? 0 : r.getContent().length();
        }
        return sum;
    }

    /** 摘要解析中间结构。 */
    private static final class SegmentDraft {
        private String summary;
        private List<String> keywords;
    }
}
