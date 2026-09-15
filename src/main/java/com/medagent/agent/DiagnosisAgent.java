package com.medagent.agent;

import com.medagent.common.Citation;
import com.medagent.common.JsonUtils;
import com.medagent.config.MedicalProperties;
import com.medagent.prompt.PromptTemplates;
import com.medagent.rag.RagService;
import com.medagent.rag.RetrievalCandidate;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 诊断智能体（DiagnosisAgent）。
 *
 * <p>ReAct 流程第二步：结合三阶段 RAG 检索证据，开启 reasoning_effort 进行鉴别诊断，
 * 输出多条假设与倾向性结论，并附带循证引用（文献/说明书来源、页码、链接、原文摘录）。</p>
 */
@Service
public class DiagnosisAgent {

    private final ChatClient chatClient;
    private final RagService ragService;
    private final String reasoningEffort;

    public DiagnosisAgent(ChatClient chatClient, RagService ragService, MedicalProperties properties) {
        this.chatClient = chatClient;
        this.ragService = ragService;
        this.reasoningEffort = properties.getThink().getReasoningEffort();
    }

    @Tool(name = "diagnose",
          description = "基于循证医学对分诊结果进行鉴别诊断，返回诊断假设、证据引用与置信度")
    public DiagnosisOutcome diagnose(String triageJson) {
        TriageResult triage = JsonUtils.fromJson(JsonUtils.cleanJson(triageJson), TriageResult.class);
        if (triage == null) {
            triage = new TriageResult();
        }
        String query = buildQuery(triage);

        // 阶段二精排后的 TopK 证据
        List<RetrievalCandidate> evidence = ragService.retrieveEvidence(query);
        String evidenceText = ragService.toEvidenceText(evidence);
        List<Citation> citations = toCitations(evidence);

        String prompt = String.format(PromptTemplates.DIAGNOSIS, triage.toCompactJson(), evidenceText);
        OpenAiChatOptions options = OpenAiChatOptions.builder()
                .reasoningEffort(reasoningEffort)
                .build();
        String json = chatClient.prompt()
                .options(options)
                .user(prompt)
                .call()
                .content();

        DiagnosisResult result = JsonUtils.fromJson(JsonUtils.cleanJson(json), DiagnosisResult.class);
        if (result == null) {
            result = new DiagnosisResult();
        }
        return new DiagnosisOutcome(result, citations, evidenceText);
    }

    private String buildQuery(TriageResult triage) {
        List<String> parts = new ArrayList<>();
        if (triage.getSymptoms() != null) {
            parts.addAll(triage.getSymptoms());
        }
        if (triage.getSuspectedDepartments() != null) {
            parts.addAll(triage.getSuspectedDepartments());
        }
        return parts.isEmpty() ? "常见症状" : String.join(" ", parts);
    }

    private List<Citation> toCitations(List<RetrievalCandidate> evidence) {
        List<Citation> list = new ArrayList<>();
        for (RetrievalCandidate c : evidence) {
            String excerpt = c.getContent() == null ? "" :
                    c.getContent().length() > 200 ? c.getContent().substring(0, 200) + "…" : c.getContent();
            list.add(new Citation(c.getSource(), c.getPage(), c.getUrl(), excerpt));
        }
        return list;
    }
}
