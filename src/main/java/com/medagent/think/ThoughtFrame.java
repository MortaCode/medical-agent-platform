package com.medagent.think;

/**
 * SSE 推送的单个思考帧（CoT 可视化单元）。
 *
 * <p>metadata 为已序列化的 JSON 字符串，承载引用文献、置信度、各阶段中间结论等，
 * 便于前端逐帧解析渲染。</p>
 */
public class ThoughtFrame {

    private int seq;
    private String stage;        // 阶段枚举名，如 SYMPTOM_DECOMPOSE
    private String stageLabel;   // 阶段中文标签
    private String content;      // 该阶段可渲染文本
    private String metadata;     // JSON 字符串
    private long timestamp;

    public static Builder builder() {
        return new Builder();
    }

    public int getSeq() { return seq; }
    public void setSeq(int seq) { this.seq = seq; }
    public String getStage() { return stage; }
    public void setStage(String stage) { this.stage = stage; }
    public String getStageLabel() { return stageLabel; }
    public void setStageLabel(String stageLabel) { this.stageLabel = stageLabel; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getMetadata() { return metadata; }
    public void setMetadata(String metadata) { this.metadata = metadata; }
    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }

    public static class Builder {
        private final ThoughtFrame f = new ThoughtFrame();

        public Builder seq(int s) { f.seq = s; return this; }
        public Builder stage(ThoughtStage stage) {
            f.stage = stage.name();
            f.stageLabel = stage.getLabel();
            return this;
        }
        public Builder content(String c) { f.content = c; return this; }
        public Builder metadata(String m) { f.metadata = m; return this; }
        public Builder timestamp(long t) { f.timestamp = t; return this; }

        public ThoughtFrame build() {
            if (f.timestamp == 0) {
                f.timestamp = System.currentTimeMillis();
            }
            return f;
        }
    }
}
