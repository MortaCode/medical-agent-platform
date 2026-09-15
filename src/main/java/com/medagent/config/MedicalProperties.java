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
        /** Token 压缩阈值，超过触发摘要压缩（默认 4000）。 */
        private int compressionTokenThreshold = 4000;
        /** 压缩后保留的最近原始消息条数。 */
        private int recentMessagesKeep = 6;
        /** 目标压缩比例（单次请求 Token 降 35%~40%）。 */
        private double targetCompressionRatio = 0.38;

        public int getCompressionTokenThreshold() { return compressionTokenThreshold; }
        public void setCompressionTokenThreshold(int v) { this.compressionTokenThreshold = v; }
        public int getRecentMessagesKeep() { return recentMessagesKeep; }
        public void setRecentMessagesKeep(int v) { this.recentMessagesKeep = v; }
        public double getTargetCompressionRatio() { return targetCompressionRatio; }
        public void setTargetCompressionRatio(double v) { this.targetCompressionRatio = v; }
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
