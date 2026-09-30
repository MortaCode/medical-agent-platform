package com.medagent.agent;

import com.medagent.common.JsonUtils;
import com.medagent.memory.MemoryContext;
import com.medagent.prompt.PromptTemplates;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Service;

/**
 * 用药审核智能体（PharmacyAgent）。
 *
 * <p>ReAct 流程第三步（并行）：对拟定用药方案做安全性审方（相互作用/禁忌/剂量/监测），
 * 输出是否通过、问题与置信度。多个用药方案可并行调用。</p>
 */
@Service
public class PharmacyAgent {

    private final ChatClient chatClient;

    public PharmacyAgent(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    /**
     * 审方（工作流第三步，可并行调用）。
     *
     * @param diagnosisJson  诊断结论 JSON
     * @param medicationPlan 待审用药方案
     * @param memoryContext  本轮记忆上下文；过敏史等关键事实必须参与审方，可为 null
     */
    @Tool(name = "review_prescription",
          description = "审核用药方案安全性：相互作用/禁忌/剂量/监测，输出 JSON（approve/issues/confidence）")
    public PharmacyResult review(String diagnosisJson, String medicationPlan, MemoryContext memoryContext) {
        MemoryContext context = memoryContext == null ? MemoryContext.empty() : memoryContext;
        String prompt = String.format(PromptTemplates.PHARMACY,
                context.toPromptBlock(), diagnosisJson, medicationPlan);
        String json = chatClient.prompt().user(prompt).call().content();
        PharmacyResult result = JsonUtils.fromJson(JsonUtils.cleanJson(json), PharmacyResult.class);
        if (result == null) {
            result = new PharmacyResult();
        }
        result.setMedication(medicationPlan);
        return result;
    }

    /** 兼容旧调用：不带记忆上下文。 */
    public PharmacyResult review(String diagnosisJson, String medicationPlan) {
        return review(diagnosisJson, medicationPlan, MemoryContext.empty());
    }
}
