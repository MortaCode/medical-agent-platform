package com.medagent.agent;

import java.util.List;

/**
 * 用药审核结果（PharmacyAgent 输出）。对应 prompts.md 的 PHARMACY 提示词 JSON 结构。
 */
public class PharmacyResult {

    private String medication;        // 被审核的用药方案
    private boolean approve;
    private List<String> issues;
    private List<String> contraindications;
    private String dosageAdvice;
    private List<String> monitoring;
    private double confidence;

    public String getMedication() { return medication; }
    public void setMedication(String m) { this.medication = m; }
    public boolean isApprove() { return approve; }
    public void setApprove(boolean approve) { this.approve = approve; }
    public List<String> getIssues() { return issues; }
    public void setIssues(List<String> issues) { this.issues = issues; }
    public List<String> getContraindications() { return contraindications; }
    public void setContraindications(List<String> c) { this.contraindications = c; }
    public String getDosageAdvice() { return dosageAdvice; }
    public void setDosageAdvice(String d) { this.dosageAdvice = d; }
    public List<String> getMonitoring() { return monitoring; }
    public void setMonitoring(List<String> m) { this.monitoring = m; }
    public double getConfidence() { return confidence; }
    public void setConfidence(double c) { this.confidence = c; }

    public String toCompactJson() {
        return com.medagent.common.JsonUtils.toJson(this);
    }
}
