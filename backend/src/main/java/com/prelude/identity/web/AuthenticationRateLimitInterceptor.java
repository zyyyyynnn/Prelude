package com.prelude.identity.web;

import com.prelude.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.HandlerInterceptor;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;

@Component
@RequiredArgsConstructor
public class AuthenticationRateLimitInterceptor implements HandlerInterceptor {

    private static final String LUA_LIMIT_SCRIPT =
        "local key = KEYS[1]\n" +
        "local capacity = tonumber(ARGV[1])\n" +
        "local rate = tonumber(ARGV[2])\n" +
        "local now = tonumber(ARGV[3])\n" +
        "local interval = 60\n" +
        "local state = redis.call('HMGET', key, 'tokens', 'last_update')\n" +
        "local tokens = tonumber(state[1])\n" +
        "local last_update = tonumber(state[2])\n" +
        "if not tokens then\n" +
        "    tokens = capacity\n" +
        "    last_update = now\n" +
        "else\n" +
        "    local elapsed = now - last_update\n" +
        "    if elapsed > 0 then\n" +
        "        tokens = math.min(capacity, tokens + elapsed * (rate / interval))\n" +
        "        last_update = now\n" +
        "    end\n" +
        "end\n" +
        "if tokens >= 1 then\n" +
        "    tokens = tokens - 1\n" +
        "    redis.call('HMSET', key, 'tokens', tokens, 'last_update', last_update)\n" +
        "    redis.call('EXPIRE', key, interval * 2)\n" +
        "    return 1\n" +
        "end\n" +
        "redis.call('HMSET', key, 'tokens', tokens, 'last_update', last_update)\n" +
        "redis.call('EXPIRE', key, interval * 2)\n" +
        "return 0";

    private final StringRedisTemplate redis;
    private final AuthenticationRateLimitProperties properties;
    private final DefaultRedisScript<Long> redisScript = new DefaultRedisScript<>(LUA_LIMIT_SCRIPT, Long.class);

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!properties.enabled()) {
            return true;
        }
        String key = key(request);
        Long allowed;
        try {
            allowed = redis.execute(
                redisScript,
                List.of(key),
                String.valueOf(properties.capacity()),
                String.valueOf(properties.requestsPerMinute()),
                String.valueOf(Instant.now().getEpochSecond())
            );
        } catch (RuntimeException exception) {
            throw new BusinessException(503, "rate_limit_unavailable", "认证服务暂时不可用");
        }
        if (allowed == null) {
            throw new BusinessException(503, "rate_limit_unavailable", "认证服务暂时不可用");
        }
        if (allowed == 0) {
            throw BusinessException.rateLimited("认证请求过于频繁，请稍后再试");
        }
        return true;
    }

    private String key(HttpServletRequest request) {
        Object matchedPattern = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        String route = matchedPattern instanceof String pattern && !pattern.isBlank()
            ? pattern
            : request.getRequestURI();
        String identity = request.getMethod() + "\n" + route + "\n" + request.getRemoteAddr();
        try {
            return "ratelimit:auth:" + HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(identity.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
