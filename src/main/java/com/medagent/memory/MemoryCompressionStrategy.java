package com.medagent.memory;

import com.medagent.common.TokenCounter;
import com.medagent.config.MedicalProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 长期记忆归档策略（话题分段式）。
 *
 * <p><b>与旧实现的关键差异</b>：旧实现把"更早的消息"整体摘要成一条 SystemMessage 塞回消息列表，
 * 带来两个问题 —— ① 用户切换话题后旧话题内容仍占据上下文并参与推理；
 * ② 下一轮压缩压缩的是"已压缩过的摘要"，多轮后医学细节持续漂移丢失。</p>
 *
 * <p>现在改为：</p>
 * <ol>
 *   <li>更早的消息按<b>话题</b>切成若干段，各自独立摘要后存入 {@link TopicSegmentStore}，
 *       <b>不再回写消息列表</b>；每个段是独立可召回单位，互不污染；</li>
 *   <li>顺带抽取跨话题常驻的医学关键事实（{@link KeyFactExtractor}）；</li>
 *   <li>消息列表只保留最近 N 条原文；历史改为请求时按相关性召回（{@link RelevantMemorySelector}）。</li>
 * </ol>
 *
 * <p>这样原始对话只被摘要一次，摘要不再被反复二次压缩；同时无关话题的历史会被相关性过滤掉。</p>
 */
@Component
@Slf4j
public class MemoryCompressionStrategy {

    private final TokenCounter tokenCounter;
    private final PersistentChatMemory chatMemory;
    private final MedicalProperties properties;
    private final TopicSegmenter topicSegmenter;
    private final TopicSegmentStore topicSegmentStore;
    private final KeyFactExtractor keyFactExtractor;

    public MemoryCompressionStrategy(TokenCounter tokenCounter,
                                     PersistentChatMemory chatMemory,
                                     MedicalProperties properties,
                                     TopicSegmenter topicSegmenter,
                                     TopicSegmentStore topicSegmentStore,
                                     KeyFactExtractor keyFactExtractor) {
        this.tokenCounter = tokenCounter;
        this.chatMemory = chatMemory;
        this.properties = properties;
        this.topicSegmenter = topicSegmenter;
        this.topicSegmentStore = topicSegmentStore;
        this.keyFactExtractor = keyFactExtractor;
    }

    /** 归档结果。 */
    public record CompressionResult(boolean compressed, int beforeTokens, int afterTokens,
                                    double reductionRatio, String summary) {
        public static CompressionResult notNeeded(int tokens) {
            return new CompressionResult(false, tokens, tokens, 0.0, null);
        }
    }

    /**
     * 若历史 Token 超阈值，则把更早的历史按话题归档为独立段，并只保留最近原文。
     *
     * @return 归档统计（未触发时 {@code compressed=false}）
     */
    public CompressionResult compressIfNeeded(String conversationId) {
        List<MessageRecord> all = chatMemory.getAllRecords(conversationId);
        int before = countTokens(all);

        int threshold = properties.getMemory().getCompressionTokenThreshold();
        if (all.isEmpty() || before <= threshold) {
            return CompressionResult.notNeeded(before);
        }

        int keep = Math.max(0, properties.getMemory().getRecentMessagesKeep());
        int split = Math.max(0, all.size() - keep);
        List<MessageRecord> older = new ArrayList<>(all.subList(0, split));
        List<MessageRecord> recent = new ArrayList<>(all.subList(split, all.size()));

        if (older.isEmpty()) {
            // 消息条数不超过保留阈值但 Token 已超限（单条超长），无可归档内容
            return CompressionResult.notNeeded(before);
        }

        // ① 抽取跨话题常驻的医学关键事实（过敏史/慢病/在服药物 —— 安全信息不可丢）
        keyFactExtractor.update(conversationId, older);

        // ② 话题分段 + 独立摘要，归档进段库（历史不再回写消息列表）
        List<TopicSegment> segments = topicSegmenter.segment(conversationId, older);
        topicSegmentStore.append(conversationId, segments,
                properties.getMemory().getMaxSegmentsRetained());

        // ③ 消息列表只保留最近原文
        chatMemory.replaceAll(conversationId, chatMemory.toMessages(recent));

        // ④ 统计：归档后的"固定注入量" = 最近原文 + 关键事实（相关段按需注入，不计入）
        MedicalKeyFacts facts = keyFactExtractor.get(conversationId);
        int after = countTokens(recent) + tokenCounter.count(facts.toPromptText());
        double ratio = tokenCounter.ratio(before, after);

        log.info("[MEMORY] 会话 " + conversationId + " 归档完成："
                + older.size() + " 条历史 → " + segments.size() + " 个话题段，"
                + "保留最近 " + recent.size() + " 条原文；固定注入 token "
                + before + " → " + after
                + "（降 " + String.format("%.1f", ratio * 100) + "%）");

        return new CompressionResult(true, before, after, ratio,
                "已归档为 " + segments.size() + " 个话题段，关键事实"
                        + (facts.isEmpty() ? "未抽取到" : "已更新"));
    }

    private int countTokens(List<MessageRecord> records) {
        return tokenCounter.count(records.stream()
                .map(MessageRecord::getContent)
                .filter(Objects::nonNull)
                .toList());
    }
}
