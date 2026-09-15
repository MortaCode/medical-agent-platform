package com.medagent.think;

/**
 * 深度思考（CoT）阶段枚举：对应 SSE 推送的六个里程碑帧。
 */
public enum ThoughtStage {

    SYMPTOM_DECOMPOSE("症状拆解", "TriageAgent 将主诉拆解为结构化症状与紧急度"),
    HYPOTHESIS_GEN("假设生成", "DiagnosisAgent 基于证据生成诊断假设"),
    EVIDENCE_RETRIEVAL("证据检索", "三阶段 RAG 精排返回的循证证据与引用"),
    DIFFERENTIAL_DIAGNOSIS("鉴别诊断", "DiagnosisAgent 给出倾向性诊断与置信度"),
    CONFIDENCE_SCORE("置信度评分", "多药师并行审方投票与加权汇总"),
    FINAL_SUGGESTION("最终建议", "综合分诊/诊断/审方的最终可行动建议");

    private final String label;
    private final String description;

    ThoughtStage(String label, String description) {
        this.label = label;
        this.description = description;
    }

    public String getLabel() { return label; }
    public String getDescription() { return description; }
}
