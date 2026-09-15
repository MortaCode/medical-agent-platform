package com.medagent.common;

import com.knuddels.jtokkit.Encodings;
import com.knuddels.jtokkit.api.Encoding;
import com.knuddels.jtokkit.api.EncodingRegistry;
import com.knuddels.jtokkit.api.ModelType;
import org.springframework.stereotype.Component;

/**
 * 基于 jtokkit 的 Token 计数器（默认按 GPT-4o 的 cl100k_base 编码估算）。
 *
 * <p>用于：① 长期记忆压缩阈值判定；② 单次请求 prompt Token 体积监控（目标降 35%~40%）。</p>
 */
@Component
public class TokenCounter {

    private final Encoding encoding;

    public TokenCounter() {
        EncodingRegistry registry = Encodings.newDefaultEncodingRegistry();
        this.encoding = registry.getEncodingForModel(ModelType.GPT_4O);
    }

    public int count(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        return encoding.countTokens(text);
    }

    /** 统计多条文本合计 Token。 */
    public int count(Iterable<String> texts) {
        int sum = 0;
        for (String t : texts) {
            sum += count(t);
        }
        return sum;
    }

    /** 估算压缩后 token（用于校验是否达成 targetRatio）。 */
    public double ratio(int before, int after) {
        if (before <= 0) {
            return 0d;
        }
        return 1.0 - (after * 1.0 / before);
    }
}
