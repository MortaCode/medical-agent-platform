package com.medagent.memory;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 话题段（Topic Segment）。
 *
 * <p>历史对话不再被压成"一条总摘要"，而是按语义边界切成若干话题段，每段各自持有摘要、
 * 关键词与摘要向量。请求到达时用当前问题与各段计算相似度，<b>只注入相关段</b>，
 * 从而避免已切换话题的旧内容污染当前推理。</p>
 *
 * <p>与旧实现的区别：旧实现把所有 older 消息揉成一条 SystemMessage 塞回消息列表，
 * 一旦用户换话题，旧内容仍占着上下文；现在历史以段的形式独立存放，按需召回。</p>
 */
@Data
public class TopicSegment {

    /** 段 ID。 */
    private String id;

    private String conversationId;

    /** 摘要（医学信息结构化保留：主诉/症状/检查/诊断假设/用药等）。 */
    private String summary;

    /** 关键词，便于日志排查与人工核对。 */
    private List<String> keywords = new ArrayList<>();

    /** 摘要向量，用于与 query 计算余弦相似度。可能为 null（embedding 不可用时）。 */
    private float[] embedding;

    /** 该段由多少条原始消息归并而来（可观测）。 */
    private int messageCount;

    private long createdAt;

    /** 最近一次被召回注入的时间（用于淘汰策略）。 */
    private long lastAccessAt;

    /** 被召回命中次数（可观测）。 */
    private int hitCount;

    /** 供日志/审计使用的短描述。 */
    public String brief() {
        String s = summary == null ? "" : summary.replaceAll("\\s+", " ");
        return s.length() > 60 ? s.substring(0, 60) + "…" : s;
    }
}
