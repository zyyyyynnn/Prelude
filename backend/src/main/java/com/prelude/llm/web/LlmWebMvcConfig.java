package com.prelude.llm.web;


import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@ConditionalOnProperty(prefix = "prelude.llm.rate-limit", name = "enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
class LlmWebMvcConfig implements WebMvcConfigurer {
    private final LlmRateLimitInterceptor interceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(interceptor)
            .addPathPatterns("/api/interview/*/chat", "/api/interview/*/finish");
    }
}
