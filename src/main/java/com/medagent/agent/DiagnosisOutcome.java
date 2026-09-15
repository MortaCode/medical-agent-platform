package com.medagent.agent;

import com.medagent.common.Citation;

import java.util.List;

/**
 * 诊断阶段产出：诊断结论 + 循证引用 + 用于 SSE 的证据原文（供溯源展示）。
 */
public class DiagnosisOutcome {

    private final DiagnosisResult diagnosis;
    private final List<Citation> citations;
    private final String evidenceText;

    public DiagnosisOutcome(DiagnosisResult diagnosis, List<Citation> citations, String evidenceText) {
        this.diagnosis = diagnosis;
        this.citations = citations;
        this.evidenceText = evidenceText;
    }

    public DiagnosisResult getDiagnosis() { return diagnosis; }
    public List<Citation> getCitations() { return citations; }
    public String getEvidenceText() { return evidenceText; }
}
