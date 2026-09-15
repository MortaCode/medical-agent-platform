package com.medagent.rag;

import com.medagent.config.MedicalProperties;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 医疗 RAG 编排门面：阶段一混合粗筛 -> 阶段二精排 -> 输出 TopK 证据。
 */
@Service
public class RagService {

    private final HybridRetriever hybridRetriever;
    private final RerankService rerankService;
    private final MedicalProperties properties;

    public RagService(HybridRetriever hybridRetriever, RerankService rerankService,
                      MedicalProperties properties) {
        this.hybridRetriever = hybridRetriever;
        this.rerankService = rerankService;
        this.properties = properties;
    }

    /**
     * 三阶段检索核心：返回精排后的 TopK 证据候选（默认 5 条）。
     */
    public List<RetrievalCandidate> retrieveEvidence(String query) {
        List<RetrievalCandidate> candidates = hybridRetriever.retrieve(query);
        return rerankService.rerank(query, candidates, properties.getRag().getRerankTopK());
    }

    /** 将证据候选拼接为可注入提示词的文本（含来源标注）。 */
    public String toEvidenceText(List<RetrievalCandidate> candidates) {
        StringBuilder sb = new StringBuilder();
        int i = 1;
        for (RetrievalCandidate c : candidates) {
            sb.append("[证据").append(i++).append("] 来源: ").append(c.getSource())
              .append(" 页码: ").append(c.getPage())
              .append(" 链接: ").append(c.getUrl()).append("\n")
              .append(c.getContent()).append("\n\n");
        }
        return sb.toString();
    }
}
