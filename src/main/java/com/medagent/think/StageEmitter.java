package com.medagent.think;

/**
 * 阶段帧发射器：由 ThinkService 实现，将各 CoT 阶段的中间结果推入 SSE 流。
 */
public interface StageEmitter {

    /**
     * @param stage    当前思考阶段
     * @param content  该阶段文本（可被前端直接渲染）
     * @param metadata 结构化元数据（引用/置信度/中间结论等，序列化为 JSON）
     */
    void emit(ThoughtStage stage, String content, Object metadata);
}
