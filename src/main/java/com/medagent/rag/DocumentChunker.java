package com.medagent.rag;

import com.medagent.common.TokenCounter;
import com.medagent.config.MedicalProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 医学文档递归切片器（基于 Token 预估）。
 *
 * <p>策略：先按段落/句/标点/空格逐级递归切分，再按 chunkSize(512 token) 合并，
 * 相邻块保留 overlap(50 token) 重叠，避免语义被切断。覆盖 12 万实体关系级别的语料。</p>
 */
@Component
public class DocumentChunker {

    private final TokenCounter tokenCounter;
    private final int chunkSize;
    private final int overlap;

    /** 递归分隔符优先级：段 -> 行 -> 句 -> 顿号 -> 逗号 -> 空格 -> 字符。 */
    private static final String[] SEPARATORS = {"\n\n", "\n", "。", "；", "，", " ", ""};

    public DocumentChunker(TokenCounter tokenCounter, MedicalProperties properties) {
        this.tokenCounter = tokenCounter;
        this.chunkSize = properties.getRag().getChunkSize();
        this.overlap = properties.getRag().getChunkOverlap();
    }

    public List<MedicalChunk> chunk(String docId, String content, String source, String page,
                                    String url, Map<String, Object> metadata) {
        List<String> pieces = recursiveSplit(content, chunkSize);
        return merge(docId, pieces, source, page, url, metadata);
    }

    /** 递归切分：使每个片段 token 数不超过 maxTokens。 */
    private List<String> recursiveSplit(String text, int maxTokens) {
        List<String> result = new ArrayList<>();
        doSplit(text, 0, maxTokens, result);
        return result;
    }

    private void doSplit(String text, int sepIdx, int maxTokens, List<String> out) {
        if (sepIdx >= SEPARATORS.length - 1 || tokenCounter.count(text) <= maxTokens) {
            out.add(text);
            return;
        }
        String sep = SEPARATORS[sepIdx];
        String[] parts = sep.isEmpty() ? text.split("") : text.split(java.util.regex.Pattern.quote(sep));
        StringBuilder current = new StringBuilder();
        for (String part : parts) {
            String candidate = current.length() == 0 ? part : current + sep + part;
            if (tokenCounter.count(candidate) > maxTokens && current.length() > 0) {
                doSplit(current.toString(), sepIdx + 1, maxTokens, out);
                current.setLength(0);
                current.append(part);
            } else {
                current.append(current.length() == 0 ? part : sep + part);
            }
        }
        if (current.length() > 0) {
            doSplit(current.toString(), sepIdx + 1, maxTokens, out);
        }
    }

    /** 合并片段为带重叠的块。 */
    private List<MedicalChunk> merge(String docId, List<String> pieces, String source, String page,
                                     String url, Map<String, Object> metadata) {
        List<MedicalChunk> chunks = new ArrayList<>();
        int i = 0;
        while (i < pieces.size()) {
            StringBuilder sb = new StringBuilder();
            int tokens = 0;
            int start = i;
            while (i < pieces.size()
                    && (tokens + tokenCounter.count(pieces.get(i))) <= chunkSize) {
                sb.append(pieces.get(i));
                tokens += tokenCounter.count(pieces.get(i));
                i++;
            }
            if (i == start) { // 单片段超长，强制纳入并前进
                sb.append(pieces.get(start));
                i++;
            }
            chunks.add(new MedicalChunk(
                    docId + "-" + chunks.size(),
                    sb.toString(),
                    source, page, url,
                    metadata));

            // 计算重叠回退步数（按平均片段 token 估算）
            int consumed = i - start;
            int avg = consumed > 0 ? Math.max(1, tokens / consumed) : chunkSize;
            int stepBack = Math.max(1, (int) Math.ceil((double) overlap / avg));
            i = Math.max(start + 1, i - stepBack);
        }
        return chunks;
    }
}
