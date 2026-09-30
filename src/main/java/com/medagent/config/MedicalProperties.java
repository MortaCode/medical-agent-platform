package com.medagent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 平台自定义配置项（application.yml 中 medical.* 前缀）。
 */
@ConfigurationProperties(prefix = "medical")
public class MedicalProperties {

    private final Memory memory = new Memory();
    private final Rag rag = new Rag();
    private final Agent agent = new Agent();
    private final Think think = new Think();

    public Memory getMemory() { return memory; }
    public Rag getRag() { return rag; }
    public Agent getAgent() { return agent; }
    public Think getThink() { return think; }

    public static class Memory {
        /** Token 压缩阈值，超过触发归档（分段摘要），默认 4000。 */
        private int compressionTokenThreshold = 4000;
        /** 压缩后保留的最近原始消息条数（保证近距指代可解析）。 */
        private int recentMessagesKeep = 6;
        /** 归档后目标：单次请求注入 Token 相对原始历史的下降比例（观测指标）。 */
        private double targetCompressionRatio = 0.38;

        // ===== 话题分段与相关性召回 =====
        /** 相邻对话轮相似度低于该值即判定为话题边界（0~1，需按 embedding 模型实测调整）。 */
        private double topicBoundaryThreshold = 0.50;
        /** 当前问题与历史话题段的相似度低于该值视为无关，不注入。 */
        private double relevantSegmentMinScore = 0.35;
        /** 单轮最多注入的历史话题段数量。 */
        private int maxRelevantSegments = 3;
        /** 话题段库保留上限，超出丢弃最旧的段，防止无限增长。 */
        private int maxSegmentsRetained = 50;
        /** 是否启用医学关键事实抽取（过敏史/慢病/在服药物，跨话题常驻注入）。 */
        private boolean keyFactsEnabled = true;

        public int getCompressionTokenThreshold() { return compressionTokenThreshold; }
        public void setCompressionTokenThreshold(int v) { this.compressionTokenThreshold = v; }
        public int getRecentMessagesKeep() { return recentMessagesKeep; }
        public void setRecentMessagesKeep(int v) { this.recentMessagesKeep = v; }
        public double getTargetCompressionRatio() { return targetCompressionRatio; }
        public void setTargetCompressionRatio(double v) { this.targetCompressionRatio = v; }
        public double getTopicBoundaryThreshold() { return topicBoundaryThreshold; }
        public void setTopicBoundaryThreshold(double v) { this.topicBoundaryThreshold = v; }
        public double getRelevantSegmentMinScore() { return relevantSegmentMinScore; }
        public void setRelevantSegmentMinScore(double v) { this.relevantSegmentMinScore = v; }
        public int getMaxRelevantSegments() { return maxRelevantSegments; }
        public void setMaxRelevantSegments(int v) { this.maxRelevantSegments = v; }
        public int getMaxSegmentsRetained() { return maxSegmentsRetained; }
        public void setMaxSegmentsRetained(int v) { this.maxSegmentsRetained = v; }
        public boolean isKeyFactsEnabled() { return keyFactsEnabled; }
        public void setKeyFactsEnabled(boolean v) { this.keyFactsEnabled = v; }
    }

    public static class Rag {
        private int vectorTopK = 50;
        private int bm25TopK = 50;
        private int hybridCandidateK = 100;
        private int rerankTopK = 5;
        private int chunkSize = 512;
        private int chunkOverlap = 50;
        private final Rerank rerank = new Rerank();

        public int getVectorTopK() { return vectorTopK; }
        public void setVectorTopK(int v) { this.vectorTopK = v; }
        public int getBm25TopK() { return bm25TopK; }
        public void setBm25TopK(int v) { this.bm25TopK = v; }
        public int getHybridCandidateK() { return hybridCandidateK; }
        public void setHybridCandidateK(int v) { this.hybridCandidateK = v; }
        public int getRerankTopK() { return rerankTopK; }
        public void setRerankTopK(int v) { this.rerankTopK = v; }
        public int getChunkSize() { return chunkSize; }
        public void setChunkSize(int v) { this.chunkSize = v; }
        public int getChunkOverlap() { return chunkOverlap; }
        public void setChunkOverlap(int v) { this.chunkOverlap = v; }
        public Rerank getRerank() { return rerank; }
    }

    public static class Rerank {
        /** cohere | local */
        private String provider = "local";
        private final Cohere cohere = new Cohere();

        public String getProvider() { return provider; }
        public void setProvider(String v) { this.provider = v; }
        public Cohere getCohere() { return cohere; }
    }

    public static class Cohere {
        private String apiKey = "";
        private String model = "rerank-english-v3.0";

        public String getApiKey() { return apiKey; }
        public void setApiKey(String v) { this.apiKey = v; }
        public String getModel() { return model; }
        public void setModel(String v) { this.model = v; }
    }

    public static class Agent {
        private double confidenceThreshold = 0.6;
        private int maxHypotheses = 5;

        public double getConfidenceThreshold() { return confidenceThreshold; }
        public void setConfidenceThreshold(double v) { this.confidenceThreshold = v; }
        public int getMaxHypotheses() { return maxHypotheses; }
        public void setMaxHypotheses(int v) { this.maxHypotheses = v; }
    }

    public static class Think {
        private String reasoningEffort = "high";
        private int sseTimeoutSeconds = 120;

        public String getReasoningEffort() { return reasoningEffort; }
        public void setReasoningEffort(String v) { this.reasoningEffort = v; }
        public int getSseTimeoutSeconds() { return sseTimeoutSeconds; }
        public void setSseTimeoutSeconds(int v) { this.sseTimeoutSeconds = v; }
    }
}
