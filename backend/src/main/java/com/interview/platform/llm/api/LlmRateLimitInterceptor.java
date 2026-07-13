package com.interview.platform.llm.api;

import com.interview.shared.api.BusinessException;
import com.interview.shared.web.UserContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.time.Instant;
import java.util.Collections;

@Slf4j
@Component
@RequiredArgsConstructor
public class LlmRateLimitInterceptor implements HandlerInterceptor {

    private final StringRedisTemplate stringRedisTemplate;

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
        "else\n" +
        "    redis.call('HMSET', key, 'tokens', tokens, 'last_update', last_update)\n" +
        "    redis.call('EXPIRE', key, interval * 2)\n" +
        "    return 0\n" +
        "end";

    private final DefaultRedisScript<Long> redisScript = new DefaultRedisScript<>(LUA_LIMIT_SCRIPT, Long.class);

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        Long userId = UserContext.getCurrentUserId();
        if (userId == null) {
            return true; // Let authentication interceptor handle unauthenticated users
        }

        String key = "ratelimit:user:" + userId;
        long now = Instant.now().getEpochSecond();
        // 10 rpm limit (capacity=10, replenish rate=10 per 60 seconds)
        Long result = stringRedisTemplate.execute(
            redisScript,
            Collections.singletonList(key),
            "10",
            "10",
            String.valueOf(now)
        );

        if (result != null && result == 0) {
            log.warn("User {} exceeded rate limit for LLM calls", userId);
            throw new BusinessException(429, "请求过于频繁，大模型接口限额 10 次/分钟，请稍后再试");
        }

        return true;
    }
}
