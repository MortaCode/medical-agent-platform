package com.medagent.agent;

import com.medagent.common.JsonUtils;
import com.medagent.memory.MemoryContext;
import com.medagent.prompt.PromptTemplates;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Service;

/**
 * 分诊智能体（TriageAgent）。
 *
 * <p>使用 {@code @Tool} 暴露为可被 LLM/路由调用工具：将患者主诉拆解为结构化症状、体征关注点、
 * 危险信号与疑似科室，并给出紧急度。ReAct 流程第一步。</p>
 */
@Service
public class TriageAgent {

    private final ChatClient chatClient;

    public TriageAgent(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    /**
     * 症状拆解与分诊。
     *
     * @param patientInput  患者本轮主诉
     * @param memoryContext 本轮记忆上下文（关键事实 + 相关历史摘要）；可为 null
     */
    @Tool(name = "triage", description = "对患者主诉进行症状拆解与分诊，输出结构化 JSON（症状/体征/危险信号/疑似科室/紧急度）")
    public TriageResult triage(String patientInput, MemoryContext memoryContext) {
        MemoryContext context = memoryContext == null ? MemoryContext.empty() : memoryContext;
        String prompt = String.format(PromptTemplates.TRIAGE, context.toPromptBlock(), patientInput);
        String json = chatClient.prompt().user(prompt).call().content();
        TriageResult result = JsonUtils.fromJson(JsonUtils.cleanJson(json), TriageResult.class);
        return result != null ? result : new TriageResult();
    }

    /** 兼容旧调用：不带记忆上下文。 */
    public TriageResult triage(String patientInput) {
        return triage(patientInput, MemoryContext.empty());
    }
}
