package com.prelude.identity.web;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "prelude.identity.auth-rate-limit")
public record AuthenticationRateLimitProperties(
    boolean enabled,
    int capacity,
    int requestsPerMinute
) {

    public AuthenticationRateLimitProperties {
        if (capacity <= 0 || requestsPerMinute <= 0) {
            throw new IllegalArgumentException("authentication rate limit capacity and requestsPerMinute must be positive");
        }
    }
}
