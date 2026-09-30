package com.medagent.think;

import com.medagent.agent.AgentRouter;
import com.medagent.agent.DiagnosisOutcome;
import com.medagent.agent.PharmacyResult;
import com.medagent.agent.ReActResult;
import com.medagent.agent.TriageResult;
import com.medagent.common.JsonUtils;
import com.medagent.memory.MemoryContext;
import com.medagent.memory.MemoryService;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/**
 * 深度思考编排服务（CoT + SSE）。
 *
 * <p>先由 {@link MemoryService} 构建本轮记忆上下文（话题分段归档 + 相关性召回 + 跨话题关键事实），
 * 再交由 {@link AgentRouter} 执行多智能体协同推理；通过 {@link StageEmitter}
 * 把每个 CoT 阶段的中间结果封装为 {@link ThoughtFrame} 逐帧推送；推理结束后把完整摘要
 * （含引用链接与阶段轨迹）写入 Elasticsearch 审计。</p>
 */
@Service
public class ThinkService {

    private final AgentRouter agentRouter;
    private final MemoryService memoryService;
    private final ReasoningAuditService auditService;
    private final ExecutorService executor = Executors.newCachedThreadPool();

    public ThinkService(AgentRouter agentRouter, MemoryService memoryService,
                        ReasoningAuditService auditService) {
        this.agentRouter = agentRouter;
        this.memoryService = memoryService;
        this.auditService = auditService;
    }

    /**
     * 返回流式 SSE 响应：逐帧推送六阶段思考过程 + 最终结论。
     *
     * @param userInput      患者/用户原始输入
     * @param conversationId 会话 ID（由拦截器注入，此处显式透传以保证异步安全）
     */
    public Flux<ServerSentEvent<ThoughtFrame>> think(String userInput, String conversationId) {
        Sinks.Many<ServerSentEvent<ThoughtFrame>> sink =
                Sinks.many().multicast().onBackpressureBuffer();

        CompletableFuture.runAsync(() -> {
            try {
                // 0) 构建本轮记忆上下文：
                //    归档更早历史（话题分段）→ 抽取跨话题关键事实 → 按相关性召回相关历史
                MemoryContext memoryContext = memoryService.buildContext(conversationId, userInput);
                memoryService.remember(conversationId, new UserMessage(userInput));

                // 阶段轨迹（用于审计）
                List<Map<String, Object>> trace = new ArrayList<>();
                int[] seq = {0};

                StageEmitter emitter = (stage, content, metadata) -> {
                    ThoughtFrame frame = ThoughtFrame.builder()
                            .seq(++seq[0])
                            .stage(stage)
                            .content(content)
                            .metadata(JsonUtils.toJson(metadata))
                            .build();
                    trace.add(Map.of(
                            "stage", stage.name(),
                            "label", stage.getLabel(),
                            "content", content,
                            "metadata", metadata));
                    sink.tryEmitNext(ServerSentEvent.builder(frame)
                            .event(stage.name())
                            .build());
                };

                // 1) 多智能体协同推理（携带记忆上下文，期间逐阶段发射帧）
                ReActResult result = agentRouter.react(userInput, emitter, memoryContext);

                memoryService.remember(conversationId, new AssistantMessage(result.getFinalSuggestion()));

                // 2) 推理摘要写入 ES 审计
                auditService.audit(buildAudit(conversationId, userInput, result, trace));

                sink.tryEmitComplete();
            } catch (Exception e) {
                sink.tryEmitError(e);
            }
        }, executor);

        return sink.asFlux();
    }

    private ReasoningAuditRecord buildAudit(String conversationId, String query,
                                           ReActResult result, List<Map<String, Object>> trace) {
        long now = System.currentTimeMillis();
        String votesJson = result.getVotes().stream()
                .map(PharmacyResult::toCompactJson)
                .collect(Collectors.joining("\n"));
        return new ReasoningAuditRecord(
                conversationId + "-" + now,
                conversationId,
                query,
                now,
                result.getFinalSuggestion(),
                result.getFinalConfidence(),
                result.getTriage().toCompactJson(),
                result.getDiagnosis().getDiagnosis().toCompactJson(),
                votesJson,
                JsonUtils.toJson(result.getCitations()),
                JsonUtils.toJson(trace));
    }
}
