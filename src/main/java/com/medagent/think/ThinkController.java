package com.medagent.think;

import com.medagent.common.ConversationContext;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

/**
 * 深度思考可视化控制器：返回 {@code Flux<ServerSentEvent<ThoughtFrame>>} 流式响应，
 * 前端可逐帧渲染 CoT 思考过程与循证引用。
 *
 * <p>应用类型为 SERVLET，但 classpath 存在 WebFlux/Reactor，MVC 可原生返回响应式 SSE 流。</p>
 */
@RestController
@RequestMapping("/api")
public class ThinkController {

    private final ThinkService thinkService;

    public ThinkController(ThinkService thinkService) {
        this.thinkService = thinkService;
    }

    /** 流式深度思考：POST /api/think  body: {"query":"患者主诉..."} */
    @PostMapping(value = "/think", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<ThoughtFrame>> think(@RequestBody ThinkRequest request) {
        String conversationId = ConversationContext.getConversationId();
        return thinkService.think(request.query(), conversationId);
    }

    /** 健康检查（已在 WebConfig 中排除拦截器，可选）。 */
    @GetMapping("/health")
    public String health() {
        return "{\"status\":\"UP\"}";
    }

    /** 请求体。 */
    public record ThinkRequest(String query) {
    }
}
