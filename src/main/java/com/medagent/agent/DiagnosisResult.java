package com.medagent.agent;

import java.util.List;

/**
 * 诊断结果（DiagnosisAgent 输出）：多条诊断假设 + 倾向性结论 + 置信度。
 */
public class DiagnosisResult {

    private List<Hypothesis> hypotheses;
    private String finalDiagnosis;
    private double confidence;

    public List<Hypothesis> getHypotheses() { return hypotheses; }
    public void setHypotheses(List<Hypothesis> h) { this.hypotheses = h; }
    public String getFinalDiagnosis() { return finalDiagnosis; }
    public void setFinalDiagnosis(String f) { this.finalDiagnosis = f; }
    public double getConfidence() { return confidence; }
    public void setConfidence(double c) { this.confidence = c; }

    public String toCompactJson() {
        return com.medagent.common.JsonUtils.toJson(this);
    }

    /** 单条诊断假设。 */
    public static class Hypothesis {
        private String name;
        private double probability;
        private List<String> supporting;
        private List<String> contradicting;
        private List<String> neededTests;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public double getProbability() { return probability; }
        public void setProbability(double p) { this.probability = p; }
        public List<String> getSupporting() { return supporting; }
        public void setSupporting(List<String> s) { this.supporting = s; }
        public List<String> getContradicting() { return contradicting; }
        public void setContradicting(List<String> c) { this.contradicting = c; }
        public List<String> getNeededTests() { return neededTests; }
        public void setNeededTests(List<String> n) { this.neededTests = n; }
    }
}
