package com.medagent.agent;

import java.util.List;

/**
 * 分诊结果（TriageAgent 输出）。对应 prompts.md 的 TRIAGE 提示词 JSON 结构。
 */
public class TriageResult {

    private List<String> symptoms;
    private List<String> vitalSignsConcern;
    private String duration;
    private List<String> redFlags;
    private List<String> suspectedDepartments;
    private String urgency; // LOW | MEDIUM | HIGH

    public List<String> getSymptoms() { return symptoms; }
    public void setSymptoms(List<String> symptoms) { this.symptoms = symptoms; }
    public List<String> getVitalSignsConcern() { return vitalSignsConcern; }
    public void setVitalSignsConcern(List<String> v) { this.vitalSignsConcern = v; }
    public String getDuration() { return duration; }
    public void setDuration(String duration) { this.duration = duration; }
    public List<String> getRedFlags() { return redFlags; }
    public void setRedFlags(List<String> redFlags) { this.redFlags = redFlags; }
    public List<String> getSuspectedDepartments() { return suspectedDepartments; }
    public void setSuspectedDepartments(List<String> s) { this.suspectedDepartments = s; }
    public String getUrgency() { return urgency; }
    public void setUrgency(String urgency) { this.urgency = urgency; }

    public String toCompactJson() {
        return com.medagent.common.JsonUtils.toJson(this);
    }
}
