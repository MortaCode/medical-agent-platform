package com.medagent;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import com.medagent.config.MedicalProperties;

/**
 * 医疗智能体平台启动类。
 *
 * <p>应用类型为 SERVLET（spring.main.web-application-type=servlet），
 * 因此 {@code MemoryInterceptor}（Spring MVC HandlerInterceptor）可正常工作；
 * 同时 classpath 存在 WebFlux/Reactor，MVC 控制器可直接返回
 * {@code Flux<ServerSentEvent<ThoughtFrame>>} 实现流式 SSE 推送。</p>
 */
@SpringBootApplication
@EnableConfigurationProperties(MedicalProperties.class)
public class MedicalAgentApplication {

    public static void main(String[] args) {
        SpringApplication.run(MedicalAgentApplication.class, args);
    }
}
