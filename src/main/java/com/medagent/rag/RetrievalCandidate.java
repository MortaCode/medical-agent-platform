package com.medagent.rag;

/**
 * 检索候选：统一混合检索（向量 / BM25）与重排阶段的传递对象。
 */
public class RetrievalCandidate {

    public enum SourceType { VECTOR, BM25 }

    private final String chunkId;
    private final String content;
    private final String source;
    private final String page;
    private final String url;
    private final SourceType sourceType;
    private double score;

    public RetrievalCandidate(String chunkId, String content, String source, String page, String url,
                              SourceType sourceType, double score) {
        this.chunkId = chunkId;
        this.content = content;
        this.source = source;
        this.page = page;
        this.url = url;
        this.sourceType = sourceType;
        this.score = score;
    }

    public String getChunkId() { return chunkId; }
    public String getContent() { return content; }
    public String getSource() { return source; }
    public String getPage() { return page; }
    public String getUrl() { return url; }
    public SourceType getSourceType() { return sourceType; }
    public double getScore() { return score; }
    public void setScore(double score) { this.score = score; }

    /** 去重键：同一块来自向量与 BM25 视为同一候选。 */
    public String dedupKey() {
        return chunkId != null ? chunkId : (source + "#" + page + "#" + content.hashCode());
    }
}
