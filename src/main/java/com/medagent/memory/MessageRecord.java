package com.medagent.memory;

import java.util.Map;

/**
 * 可持久化的消息记录（与 Spring AI {@code Message} 解耦，避免 JSON 多态反序列化问题）。
 */
public class MessageRecord {

    /** 角色：USER / ASSISTANT / SYSTEM / TOOL */
    private String role;
    private String content;
    private Map<String, Object> metadata;

    public MessageRecord() {
    }

    public MessageRecord(String role, String content, Map<String, Object> metadata) {
        this.role = role;
        this.content = content;
        this.metadata = metadata;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
    }
}
