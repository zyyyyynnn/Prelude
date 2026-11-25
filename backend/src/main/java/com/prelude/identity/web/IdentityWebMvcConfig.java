package com.prelude.identity.web;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@EnableConfigurationProperties(AuthenticationRateLimitProperties.class)
@RequiredArgsConstructor
public class IdentityWebMvcConfig implements WebMvcConfigurer {

    private final AuthenticationRateLimitInterceptor authenticationRateLimitInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authenticationRateLimitInterceptor)
            .addPathPatterns("/api/auth/login", "/api/auth/register");
    }
}
