package com.medagent.config;

import com.medagent.memory.MemoryInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 注册 Spring MVC 拦截器。应用类型必须为 SERVLET（见 application.yml），
 * 否则 HandlerInterceptor 不会生效。
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final MemoryInterceptor memoryInterceptor;

    public WebConfig(MemoryInterceptor memoryInterceptor) {
        this.memoryInterceptor = memoryInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(memoryInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns("/api/health");
    }
}
