package com.medagent.memory;

import java.util.List;

/**
 * 本轮请求的记忆上下文包。
 *
 * <p>由 {@link MemoryService#buildContext} 组装，交给各 Agent 拼进提示词。三层结构：</p>
 * <ol>
 *   <li><b>关键事实层</b>（{@link MedicalKeyFacts}）—— 跨话题常驻，无条件注入；</li>
 *   <li><b>相关历史层</b>（{@link TopicSegment}）—— 只含与当前问题相关的历史摘要；</li>
 *   <li><b>最近原文层</b>（recentMessages）—— 保证近距指代可解析。</li>
 * </ol>
 *
 * <p>当判定为话题切换（当前问题与全部历史段的相似度均低于阈值）时，
 * {@link #getRelevantSegments()} 为空 —— 旧话题内容不会被注入，当前问题在干净上下文中被理解。</p>
 */
public class MemoryContext {

    private final MedicalKeyFacts keyFacts;
    private final List<TopicSegment> relevantSegments;
    private final List<MessageRecord> recentMessages;
    /** 是否检测到话题切换（历史与当前问题无关）。 */
    private final boolean topicShiftDetected;
    /** 是否因 embedding 不可用而降级（降级时不注入历史段，保守避免污染）。 */
    private final boolean degraded;
    /** 当前问题与历史段的最高相似度，用于观测与阈值调参。 */
    private final double topScore;

    public MemoryContext(MedicalKeyFacts keyFacts,
                         List<TopicSegment> relevantSegments,
                         List<MessageRecord> recentMessages,
                         boolean topicShiftDetected,
                         boolean degraded,
                         double topScore) {
        this.keyFacts = keyFacts;
        this.relevantSegments = relevantSegments == null ? List.of() : relevantSegments;
        this.recentMessages = recentMessages == null ? List.of() : recentMessages;
        this.topicShiftDetected = topicShiftDetected;
        this.degraded = degraded;
        this.topScore = topScore;
    }

    /** 无记忆场景（如未启用记忆的调用方）。 */
    public static MemoryContext empty() {
        return new MemoryContext(null, List.of(), List.of(), false, false, 0d);
    }

    public MedicalKeyFacts getKeyFacts() { return keyFacts; }
    public List<TopicSegment> getRelevantSegments() { return relevantSegments; }
    public List<MessageRecord> getRecentMessages() { return recentMessages; }
    public boolean isTopicShiftDetected() { return topicShiftDetected; }
    public boolean isDegraded() { return degraded; }
    public double getTopScore() { return topScore; }

    public boolean hasKeyFacts() {
        return keyFacts != null && !keyFacts.isEmpty();
    }

    public boolean hasRelevantHistory() {
        return !relevantSegments.isEmpty();
    }

    public String keyFactsText() {
        return hasKeyFacts() ? keyFacts.toPromptText() : "";
    }

    /** 相关历史摘要文本块；无相关段时返回空串。 */
    public String relevantHistoryText() {
        if (relevantSegments.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("【相关历史对话摘要 · 已按相关性筛选，与当前问题无关的历史已剔除】\n");
        int i = 1;
        for (TopicSegment segment : relevantSegments) {
            sb.append("[").append(i++).append("] ").append(segment.getSummary()).append('\n');
        }
        return sb.toString();
    }

    /**
     * 组装为可直接拼进提示词的完整块。
     *
     * @return 文本块；无任何可注入内容时返回空串
     */
    public String toPromptBlock() {
        StringBuilder sb = new StringBuilder();
        String facts = keyFactsText();
        if (!facts.isEmpty()) {
            sb.append(facts);
        }
        String history = relevantHistoryText();
        if (!history.isEmpty()) {
            if (sb.length() > 0) {
                sb.append('\n');
            }
            sb.append(history);
        }
        return sb.toString();
    }

    /** 供日志与可观测使用的一行摘要。 */
    public String describe() {
        return "keyFacts=" + hasKeyFacts()
                + ", relevantSegments=" + relevantSegments.size()
                + ", recentMessages=" + recentMessages.size()
                + ", topScore=" + String.format("%.3f", topScore)
                + ", topicShift=" + topicShiftDetected
                + (degraded ? ", degraded=true" : "");
    }
}
