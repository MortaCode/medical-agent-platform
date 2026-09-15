package com.medagent.rag;

import java.util.Map;

/**
 * 医学文档切片（含循证溯源信息：来源文献/说明书、页码/章节）。
 */
public class MedicalChunk {

    private String chunkId;
    private String content;
    private String source;     // 文献/说明书名称
    private String page;       // 页码或章节（用于引用溯源）
    private String url;        // 在线来源链接（SSE/审计引用）
    private Map<String, Object> metadata;

    public MedicalChunk() {
    }

    public MedicalChunk(String chunkId, String content, String source, String page, String url,
                        Map<String, Object> metadata) {
        this.chunkId = chunkId;
        this.content = content;
        this.source = source;
        this.page = page;
        this.url = url;
        this.metadata = metadata;
    }

    public String getChunkId() { return chunkId; }
    public void setChunkId(String chunkId) { this.chunkId = chunkId; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public String getPage() { return page; }
    public void setPage(String page) { this.page = page; }
    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }
    public Map<String, Object> getMetadata() { return metadata; }
    public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata; }
}
