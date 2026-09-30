package com.medagent.memory;

import com.medagent.config.MedicalProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 相关历史召回器：判断"历史与当前问题是否相关"，并只选出相关的话题段。
 *
 * <p>这是解决"更早的对话与最新问题无关"的核心部件。判定方式为向量余弦相似度：</p>
 * <ul>
 *   <li>当前问题与<b>全部</b>历史段的最高相似度仍低于阈值 → 判定为<b>话题切换</b>，
 *       本轮不注入任何历史摘要，当前问题在干净上下文中被理解；</li>
 *   <li>存在相似度达标的段 → 只注入这些段（最多 {@code maxRelevantSegments} 个），
 *       无关话题的内容不会进入提示词。</li>
 * </ul>
 *
 * <p>若向量不可用（embedding 故障），采取<b>保守降级</b>：不注入历史段。
 * 宁可少给上下文，也不让未知相关性的旧内容污染当前推理。</p>
 */
@Component
@Slf4j
public class RelevantMemorySelector {

    private final EmbeddingSupport embeddingSupport;
    private final MedicalProperties properties;
    private final TopicSegmentStore segmentStore;

    public RelevantMemorySelector(EmbeddingSupport embeddingSupport,
                                  MedicalProperties properties,
                                  TopicSegmentStore segmentStore) {
        this.embeddingSupport = embeddingSupport;
        this.properties = properties;
        this.segmentStore = segmentStore;
    }

    /**
     * @param conversationId 会话 ID（用于记录段命中）
     * @param query          当前问题原文
     * @param segments       该会话的全部历史话题段
     */
    public SelectionResult select(String conversationId, String query, List<TopicSegment> segments) {
        if (segments == null || segments.isEmpty()) {
            return SelectionResult.empty();
        }

        float[] queryVector = embeddingSupport.embed(query);
        if (queryVector == null) {
            log.warn("[MEMORY] 当前问题向量不可用，本轮不注入历史段（保守降级，避免污染）");
            return new SelectionResult(List.of(), 0d, false, true);
        }

        double minScore = properties.getMemory().getRelevantSegmentMinScore();
        int maxSegments = properties.getMemory().getMaxRelevantSegments();

        List<Scored> scored = new ArrayList<>(segments.size());
        for (TopicSegment segment : segments) {
            scored.add(new Scored(segment, embeddingSupport.cosine(queryVector, segment.getEmbedding())));
        }
        scored.sort(Comparator.comparingDouble(Scored::score).reversed());

        double topScore = scored.get(0).score();
        if (topScore < minScore) {
            log.info("[MEMORY] 判定为话题切换：与 " + segments.size() + " 个历史段的最高相似度 "
                    + String.format("%.3f", topScore) + " < 阈值 " + minScore + "，本轮不注入历史摘要");
            return new SelectionResult(List.of(), topScore, true, false);
        }

        List<TopicSegment> selected = new ArrayList<>();
        for (Scored item : scored) {
            if (item.score() < minScore || selected.size() >= maxSegments) {
                break;
            }
            selected.add(item.segment());
        }
        for (TopicSegment segment : selected) {
            segmentStore.touch(conversationId, segment.getId());
        }
        log.info("[MEMORY] 召回 " + selected.size() + " 个相关历史段（最高相似度 "
                + String.format("%.3f", topScore) + "）");
        return new SelectionResult(selected, topScore, false, false);
    }

    /**
     * 召回结果。
     *
     * @param segments   命中的相关段（可能为空）
     * @param topScore   当前问题与历史段的最高相似度
     * @param topicShift 是否判定为话题切换
     * @param degraded   是否因向量不可用而降级
     */
    public record SelectionResult(List<TopicSegment> segments,
                                  double topScore,
                                  boolean topicShift,
                                  boolean degraded) {

        public static SelectionResult empty() {
            return new SelectionResult(List.of(), 0d, false, false);
        }
    }

    private record Scored(TopicSegment segment, double score) {
    }
}
