package com.medagent.rag;

import java.util.List;

/**
 * 阶段二精排服务接口：对阶段一混合召回的候选集重排，输出 TopK 最相关文档。
 */
public interface RerankService {

    /**
     * @param query      原始查询
     * @param candidates 混合召回候选（约 100）
     * @param topK       输出条数（默认 5）
     * @return 重排后的候选（score 已被重新赋值）
     */
    List<RetrievalCandidate> rerank(String query, List<RetrievalCandidate> candidates, int topK);
}
