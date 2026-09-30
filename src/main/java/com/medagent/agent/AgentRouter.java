package com.medagent.agent;

import com.medagent.common.Citation;
import com.medagent.config.MedicalProperties;
import com.medagent.memory.MemoryContext;
import com.medagent.prompt.PromptTemplates;
import com.medagent.think.StageEmitter;
import com.medagent.think.ThoughtStage;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * 多智能体协同路由器（ReAct 编排）。
 *
 * <p>流程：
 * → ① TriageAgent 拆解症状（SYMPTOM_DECOMPOSE）
 * → ② DiagnosisAgent 检索证据 + 深度思考提假设（EVIDENCE_RETRIEVAL / HYPOTHESIS_GEN / DIFFERENTIAL_DIAGNOSIS）
 * → ③ 并行 PharmacyAgent 对多个候选用药方案审方（投票）
 * → ④ 加权/投票汇总 + 最终综合建议（CONFIDENCE_SCORE / FINAL_SUGGESTION）</p>
 */
@Component
public class AgentRouter {

    private final TriageAgent triageAgent;
    private final DiagnosisAgent diagnosisAgent;
    private final PharmacyAgent pharmacyAgent;
    private final ChatClient chatClient;
    private final MedicalProperties properties;

    public AgentRouter(TriageAgent triageAgent, DiagnosisAgent diagnosisAgent,
                       PharmacyAgent pharmacyAgent, ChatClient chatClient,
                       MedicalProperties properties) {
        this.triageAgent = triageAgent;
        this.diagnosisAgent = diagnosisAgent;
        this.pharmacyAgent = pharmacyAgent;
        this.chatClient = chatClient;
        this.properties = properties;
    }

    /**
     * 执行多智能体协同推理。
     *
     * @param userInput     患者本轮输入
     * @param emitter       CoT 阶段发射器
     * @param memoryContext 本轮记忆上下文（关键事实 + 相关历史摘要）；可为 null
     */
    public ReActResult react(String userInput, StageEmitter emitter, MemoryContext memoryContext) {
        MemoryContext context = memoryContext == null ? MemoryContext.empty() : memoryContext;

        // ① 分诊
        TriageResult triage = triageAgent.triage(userInput, context);
        String triageJson = triage.toCompactJson();
        emitter.emit(ThoughtStage.SYMPTOM_DECOMPOSE,
                "分诊完成：症状=" + triage.getSymptoms() + "；紧急度=" + triage.getUrgency(),
                triage);

        // ② 诊断（含 RAG 证据 + reasoning_effort）
        DiagnosisOutcome outcome = diagnosisAgent.diagnose(triageJson, context);
        String diagnosisJson = outcome.getDiagnosis().toCompactJson();
        emitter.emit(ThoughtStage.EVIDENCE_RETRIEVAL,
                outcome.getEvidenceText(), outcome.getCitations());
        emitter.emit(ThoughtStage.HYPOTHESIS_GEN,
                "生成诊断假设：" + outcome.getDiagnosis().getHypotheses(), outcome.getDiagnosis());
        emitter.emit(ThoughtStage.DIFFERENTIAL_DIAGNOSIS,
                "倾向性诊断=" + outcome.getDiagnosis().getFinalDiagnosis()
                        + "；置信度=" + outcome.getDiagnosis().getConfidence(),
                outcome.getDiagnosis());

        // ③ 生成候选用药方案并【并行】审方
        List<String> plans = proposeMedicationPlans(diagnosisJson);           //开方
        List<PharmacyResult> votes = parallelReview(diagnosisJson, plans, context);    //异步审方

        // ④ 加权汇总 + 最终综合建议
        double finalConfidence = weightedConfidence(votes);                        //加权评分
        emitter.emit(ThoughtStage.CONFIDENCE_SCORE,
                "并行审方投票完成，加权置信度=" + finalConfidence, votes);
        String finalSuggestion = synthesize(triageJson, diagnosisJson, votes);      //最终建议
        emitter.emit(ThoughtStage.FINAL_SUGGESTION, finalSuggestion,
                java.util.Map.of("confidence", finalConfidence, "citations", outcome.getCitations()));

        // 合并引用：诊断证据 + 审方引用
        List<Citation> citations = new ArrayList<>(outcome.getCitations());

        return new ReActResult(triage, outcome, votes, finalSuggestion, finalConfidence, citations);
    }

    /** 兼容旧调用：不带记忆上下文。 */
    public ReActResult react(String userInput, StageEmitter emitter) {
        return react(userInput, emitter, MemoryContext.empty());
    }

    /** 基于诊断结论提出候选用药方案（每行一个）。 */
    private List<String> proposeMedicationPlans(String diagnosisJson) {
        String prompt = "基于以下诊断结论，提出 1-3 个候选用药方案，每行一个方案，只写方案文本不要解释：\n"
                + diagnosisJson;
        String text = chatClient.prompt().user(prompt).call().content();
        return text.lines()
                .map(String::trim)
                .filter(l -> !l.isEmpty())
                .limit(properties.getAgent().getMaxHypotheses())
                .collect(Collectors.toList());
    }

    /** 并行调用 PharmacyAgent 审方（工作流并行分支）。 */
    private List<PharmacyResult> parallelReview(String diagnosisJson, List<String> plans,
                                                MemoryContext context) {
        if (plans.isEmpty()) {
            return List.of();
        }
        List<CompletableFuture<PharmacyResult>> futures = plans.stream()
                .map(plan -> CompletableFuture.supplyAsync(
                        () -> pharmacyAgent.review(diagnosisJson, plan, context)))
                .toList();
        List<PharmacyResult> votes = new ArrayList<>();
        for (CompletableFuture<PharmacyResult> f : futures) {
            votes.add(f.join());
        }
        return votes;
    }

    /** 投票/加权汇总：通过率与平均置信度加权。 */
    private double weightedConfidence(List<PharmacyResult> votes) {
        if (votes.isEmpty()) {
            return 0d;
        }
        long approved = votes.stream().filter(PharmacyResult::isApprove).count();
        double avgConf = votes.stream().mapToDouble(PharmacyResult::getConfidence).average().orElse(0d);
        double approvalRate = approved * 1.0 / votes.size();
        // 通过率(60%) + 平均置信度(40%)
        return Math.round((approvalRate * 0.6 + avgConf * 0.4) * 100.0) / 100.0;
    }

    private String synthesize(String triageJson, String diagnosisJson, List<PharmacyResult> votes) {
        String votesJson = votes.stream()
                .map(PharmacyResult::toCompactJson)
                .collect(Collectors.joining("\n"));
        String prompt = String.format(PromptTemplates.FINAL_SYNTHESIS, triageJson, diagnosisJson, votesJson);
        return chatClient.prompt().user(prompt).call().content();
    }
}
