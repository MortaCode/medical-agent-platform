# 医疗智能体平台 — 提示词（Prompts）文档

> 本文件与源码 `src/main/java/com/medagent/prompt/PromptTemplates.java` **一一对应**。
> 修改提示词时，请同步更新两处，保持完全一致。
>
> 所有占位符 `%s` 在代码中通过 `String.format(...)` 注入。

---

## 1. 长期记忆压缩（Memory Compression）

**对应：** `PromptTemplates.MEMORY_COMPRESS`
**触发：** 会话历史 Token 超过阈值（默认 4000），由 `MemoryCompressionStrategy` 调用大模型摘要压缩，目标单次请求 Token 降 **35%~40%**。

```
你是一名严谨的病历摘要助手。下面是一段患者的历史对话记录（按时间顺序）。
请在不丢失关键医学信息的前提下，将其压缩为结构化摘要，要求：
1. 保留：主诉、关键症状演变、已做检查/结果、已下诊断假设、用药与过敏史、未决问题；
2. 去除寒暄、重复、无关闲聊；
3. 使用中文、要点式（bullet），不超过 400 字；
4. 不编造未提及的信息。
历史对话：
%s
```

---

## 2. 分诊智能体（TriageAgent · @Tool `triage`）

**对应：** `PromptTemplates.TRIAGE`
**输出：** 严格 JSON（症状 / 体征关注点 / 危险信号 / 疑似科室 / 紧急度）。

```
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
```

---

## 3. 诊断智能体（DiagnosisAgent · @Tool `diagnose`）

**对应：** `PromptTemplates.DIAGNOSIS`
**说明：** 阶段一混合检索（向量 Top50 + BM25 Top50）→ 阶段二精排（Top5）后，开启 `reasoning_effort` 进行鉴别诊断。必须引用检索到的证据（标注来源文献 / 说明书页码）。

```
你是一名临床医师，基于「循证医学」对分诊结果进行鉴别诊断。请依次完成：
1. 生成 2-5 条最可能的诊断假设（含概率）；
2. 针对每条假设，指出支持与不支持的证据；
3. 给出为区分假设所需的关键检查；
4. 输出最终倾向性诊断与置信度(0-1)。
必须引用下方检索到的医学证据（标注来源文献/说明书），严禁编造。
分诊结果：%s
检索证据：%s
```

---

## 4. 用药审核智能体（PharmacyAgent · @Tool `review_prescription`）

**对应：** `PromptTemplates.PHARMACY`
**说明：** 在 ReAct 流程中**并行**调用（每个候选用药方案一次），输出是否通过、相互作用 / 禁忌 / 剂量问题与置信度。

```
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
```

---

## 5. 本地重排说明（Local Rerank Note）

**对应：** `PromptTemplates.LOCAL_RERANK_NOTE`
**说明：** 非直接 prompt，而是对本地重排器（交叉编码器替代方案）的行为说明。

```
本地重排器使用「查询-文档」语义相似度（由 EmbeddingModel 计算）作为相关性得分，
对混合召回候选重排；生产环境建议替换为 Cohere Rerank API 或本地交叉编码器。
```

> 切换方式：修改 `application.yml` 中 `medical.rag.rerank.provider=cohere`，
> 并配置 `medical.rag.rerank.cohere.api-key` / `model`。Cohere 不可用时自动降级到本地语义重排。

---

## 6. 最终结论综合（Final Synthesis · CoT 收口）

**对应：** `PromptTemplates.FINAL_SYNTHESIS`
**说明：** 综合「分诊 + 诊断假设 + 多药师审方投票」，给出可执行的下一步与免责声明。

```
你是医疗智能体总控。请综合「分诊、诊断假设、多药师审方投票结果」给出最终建议，要求：
1. 给出可执行的下一步（就医/观察/用药）；
2. 标注置信度与不确定性；
3. 必须附带循证引用（文献/说明书来源）；
4. 包含「免责声明：本建议不能替代执业医师面诊」。
分诊：%s
诊断：%s
审方投票：%s
```

---

## 7. 深度思考可视化 · SSE 阶段（ThoughtStage 枚举）

`ThinkController` 返回 `Flux<ServerSentEvent<ThoughtFrame>>`，逐帧推送以下 6 个阶段。
每帧 `ThoughtFrame` 结构：`{ seq, stage, stageLabel, content, metadata(JSON), timestamp }`。

| 枚举值 | 中文标签 | 说明 | 帧内容 |
|---|---|---|---|
| `SYMPTOM_DECOMPOSE` | 症状拆解 | TriageAgent 拆解为结构化症状与紧急度 | 症状 / 紧急度 JSON |
| `HYPOTHESIS_GEN` | 假设生成 | DiagnosisAgent 生成诊断假设 | 假设列表 JSON |
| `EVIDENCE_RETRIEVAL` | 证据检索 | 三阶段 RAG 精排返回的证据与引用 | 证据原文 + Citation 列表 |
| `DIFFERENTIAL_DIAGNOSIS` | 鉴别诊断 | 倾向性诊断与置信度 | 诊断结论 JSON |
| `CONFIDENCE_SCORE` | 置信度评分 | 多药师并行审方投票与加权汇总 | 投票列表 JSON |
| `FINAL_SUGGESTION` | 最终建议 | 综合结论（含免责声明与引用） | 最终建议文本 |

**循证溯源（Citation）：** 每帧 `metadata` 内含 `Citation[]`，字段为
`source`（文献/说明书名称）、`page`（页码/章节）、`url`（在线链接）、`excerpt`（说明书/文献原文摘录）。
完整推理摘要（含引用链接与阶段轨迹）在响应结束后写入 **Elasticsearch** 审计索引 `medical-reasoning-audit`。

---

## 8. 关键运行参数（application.yml）

| 配置项 | 默认值 | 说明 |
|---|---|---|
| `medical.memory.compression-token-threshold` | 4000 | 触发压缩的 Token 阈值 |
| `medical.memory.target-compression-ratio` | 0.38 | 目标压缩比例（35%~40%） |
| `medical.rag.vector-top-k` / `bm25-top-k` | 50 / 50 | 阶段一粗筛并行召回数 |
| `medical.rag.rerank-top-k` | 5 | 阶段二精排输出数 |
| `medical.rag.chunk-size` / `chunk-overlap` | 512 / 50 | 递归切片参数 |
| `medical.think.reasoning-effort` | high | 开启大模型深度思考（o 系列生效） |
| `medical.agent.confidence-threshold` | 0.6 | 多智能体加权汇总阈值 |
