package com.medagent.prompt;

/**
 * 全平台提示词集中管理。
 *
 * <p>本类中的常量与项目根目录 {@code prompts.md} 一一对应（markdown 为可读文档，
 * 此处为代码实际引用来源）。修改提示词请同步两处。</p>
 */
public final class PromptTemplates {

    private PromptTemplates() {
    }

    /** ============ 模块1：长期记忆压缩 ============ */
    public static final String MEMORY_COMPRESS =
            """
            你是一名严谨的病历摘要助手。下面是一段患者的历史对话记录（按时间顺序）。
            请在不丢失关键医学信息的前提下，将其压缩为结构化摘要，要求：
            1. 保留：主诉、关键症状演变、已做检查/结果、已下诊断假设、用药与过敏史、未决问题；
            2. 去除寒暄、重复、无关闲聊；
            3. 使用中文、要点式（bullet），不超过 400 字；
            4. 不编造未提及的信息。
            历史对话：
            %s
            """;

    /** ============ 模块3：分诊 Agent（Triage） ============ */
    public static final String TRIAGE =
            """
            你是急诊分诊护士。请对患者输入进行「症状拆解与分诊」，严格输出 JSON：
            {
              "symptoms": ["症状1","症状2"],
              "vital_signs_concern": ["需重点关注的体征"],
              "duration": "症状持续时间",
              "red_flags": ["危险信号，若有"],
              "suspected_departments": ["疑似科室/系统"],
              "urgency": "LOW|MEDIUM|HIGH"
            }
            仅输出 JSON，不要解释。患者输入：%s
            """;

    /** ============ 模块3：诊断 Agent（Diagnosis，含深度思考） ============ */
    public static final String DIAGNOSIS =
            """
            你是一名临床医师，基于「循证医学」对分诊结果进行鉴别诊断。请依次完成：
            1. 生成 2-5 条最可能的诊断假设（含概率）；
            2. 针对每条假设，指出支持与不支持的证据；
            3. 给出为区分假设所需的关键检查；
            4. 输出最终倾向性诊断与置信度(0-1)。
            必须引用下方检索到的医学证据（标注来源文献/说明书），严禁编造。
            分诊结果：%s
            检索证据：%s
            """;

    /** ============ 模块3：用药审核 Agent（Pharmacy） ============ */
    public static final String PHARMACY =
            """
            你是临床药师，审核处方安全性。请对给定「诊断 + 拟定用药方案」进行审方，输出 JSON：
            {
              "approve": true|false,
              "issues": ["相互作用/禁忌/剂量问题..."],
              "contraindications": ["禁忌"],
              "dosage_advice": "剂量与疗程建议",
              "monitoring": ["需监测的指标"],
              "confidence": 0.0-1.0
            }
            严格依据说明书原文与证据，标注引用来源。诊断：%s；用药方案：%s
            """;

    /** ============ 模块2：本地重排打分指令（作为 CoT 说明，非直接 prompt） ============ */
    public static final String LOCAL_RERANK_NOTE =
            "本地重排器使用「查询-文档」语义相似度（由 EmbeddingModel 计算）作为相关性得分，"
            + "对混合召回候选重排；生产环境建议替换为 Cohere Rerank API 或本地交叉编码器。";

    /** ============ 模块4：最终结论综合（CoT 收口） ============ */
    public static final String FINAL_SYNTHESIS =
            """
            你是医疗智能体总控。请综合「分诊、诊断假设、多药师审方投票结果」给出最终建议，要求：
            1. 给出可执行的下一步（就医/观察/用药）；
            2. 标注置信度与不确定性；
            3. 必须附带循证引用（文献/说明书来源）；
            4. 包含「免责声明：本建议不能替代执业医师面诊」。
            分诊：%s
            诊断：%s
            审方投票：%s
            """;
}
