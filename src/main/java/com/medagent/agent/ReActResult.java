package com.medagent.agent;

import com.medagent.common.Citation;

import java.util.List;

/**
 * 多智能体 ReAct 编排结果：包含各阶段中间产出、并行审方投票、最终综合建议与全部循证引用。
 */
public class ReActResult {

    private final TriageResult triage;
    private final DiagnosisOutcome diagnosis;
    private final List<PharmacyResult> votes;
    private final String finalSuggestion;
    private final double finalConfidence;
    private final List<Citation> citations;

    public ReActResult(TriageResult triage, DiagnosisOutcome diagnosis,
                       List<PharmacyResult> votes, String finalSuggestion,
                       double finalConfidence, List<Citation> citations) {
        this.triage = triage;
        this.diagnosis = diagnosis;
        this.votes = votes;
        this.finalSuggestion = finalSuggestion;
        this.finalConfidence = finalConfidence;
        this.citations = citations;
    }

    public TriageResult getTriage() { return triage; }
    public DiagnosisOutcome getDiagnosis() { return diagnosis; }
    public List<PharmacyResult> getVotes() { return votes; }
    public String getFinalSuggestion() { return finalSuggestion; }
    public double getFinalConfidence() { return finalConfidence; }
    public List<Citation> getCitations() { return citations; }
}
