package com.medagent.think;

/**
 * 推理审计记录：完整推理摘要日志（含引用链接），落库 Elasticsearch 供合规与回溯。
 */
public class ReasoningAuditRecord {

    private String id;                 // conversationId + "-" + timestamp
    private String conversationId;
    private String query;
    private long createdAt;
    private String finalSuggestion;
    private double finalConfidence;
    private String triageJson;
    private String diagnosisJson;
    private String votesJson;
    private String citationsJson;      // 引用链接集中存放
    private String stageTraceJson;     // 各阶段中间结果轨迹

    public ReasoningAuditRecord() {
    }

    public ReasoningAuditRecord(String id, String conversationId, String query, long createdAt,
                               String finalSuggestion, double finalConfidence, String triageJson,
                               String diagnosisJson, String votesJson, String citationsJson,
                               String stageTraceJson) {
        this.id = id;
        this.conversationId = conversationId;
        this.query = query;
        this.createdAt = createdAt;
        this.finalSuggestion = finalSuggestion;
        this.finalConfidence = finalConfidence;
        this.triageJson = triageJson;
        this.diagnosisJson = diagnosisJson;
        this.votesJson = votesJson;
        this.citationsJson = citationsJson;
        this.stageTraceJson = stageTraceJson;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getConversationId() { return conversationId; }
    public void setConversationId(String c) { this.conversationId = c; }
    public String getQuery() { return query; }
    public void setQuery(String q) { this.query = q; }
    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long t) { this.createdAt = t; }
    public String getFinalSuggestion() { return finalSuggestion; }
    public void setFinalSuggestion(String s) { this.finalSuggestion = s; }
    public double getFinalConfidence() { return finalConfidence; }
    public void setFinalConfidence(double c) { this.finalConfidence = c; }
    public String getTriageJson() { return triageJson; }
    public void setTriageJson(String t) { this.triageJson = t; }
    public String getDiagnosisJson() { return diagnosisJson; }
    public void setDiagnosisJson(String d) { this.diagnosisJson = d; }
    public String getVotesJson() { return votesJson; }
    public void setVotesJson(String v) { this.votesJson = v; }
    public String getCitationsJson() { return citationsJson; }
    public void setCitationsJson(String c) { this.citationsJson = c; }
    public String getStageTraceJson() { return stageTraceJson; }
    public void setStageTraceJson(String s) { this.stageTraceJson = s; }
}
