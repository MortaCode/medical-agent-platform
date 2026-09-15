package com.medagent.think;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.IndexRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 推理审计服务：将完整推理摘要（含引用链接与阶段轨迹）写入 Elasticsearch，
 * 满足医疗合规与可回溯要求。索引名：medical-reasoning-audit。
 */
@Service
public class ReasoningAuditService {

    private static final Logger log = LoggerFactory.getLogger(ReasoningAuditService.class);
    private static final String INDEX = "medical-reasoning-audit";

    private final ElasticsearchClient esClient;

    public ReasoningAuditService(ElasticsearchClient esClient) {
        this.esClient = esClient;
    }

    public void audit(ReasoningAuditRecord record) {
        try {
            IndexRequest<ReasoningAuditRecord> req = IndexRequest.of(b -> b
                    .index(INDEX)
                    .id(record.getId())
                    .document(record));
            esClient.index(req);
            log.info("[AUDIT] 推理摘要已写入 ES：id={}, conversationId={}",
                    record.getId(), record.getConversationId());
        } catch (Exception e) {
            // 审计失败不应中断主链路
            log.warn("[AUDIT] 写入 ES 失败（不影响主响应）：{}", e.getMessage());
        }
    }
}
