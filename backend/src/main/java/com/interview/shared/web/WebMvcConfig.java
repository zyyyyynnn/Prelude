package com.interview.shared.web;

import com.interview.platform.llm.api.LlmRateLimitInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;

@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final JwtInterceptor jwtInterceptor;
    private final LlmRateLimitInterceptor llmRateLimitInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(jwtInterceptor)
            .addPathPatterns("/api/**")
            .excludePathPatterns("/api/auth/**", "/api/health", "/api/llm/providers", "/api/dev-fixtures/reset");

        registry.addInterceptor(llmRateLimitInterceptor)
            .addPathPatterns("/api/interview/*/chat", "/api/interview/*/finish");
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String uploadPath = Path.of("uploads").toAbsolutePath().normalize().toUri().toString();
        registry.addResourceHandler("/uploads/**")
            .addResourceLocations(uploadPath);
    }
}
