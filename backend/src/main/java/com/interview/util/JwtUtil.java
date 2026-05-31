package com.interview.util;

import com.interview.common.BusinessException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

@Component
public class JwtUtil {

    private final SecretKey secretKey;
    private final long expireDays;

    public JwtUtil(
        @Value("${jwt.secret}") String secret,
        @Value("${jwt.expire-days}") long expireDays
    ) {
        if (secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalArgumentException("jwt.secret length must be at least 32 bytes");
        }
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expireDays = expireDays;
    }

    public String generateToken(Long userId) {
        Instant now = Instant.now();
        return Jwts.builder()
            .subject(String.valueOf(userId))
            .issuedAt(Date.from(now))
            .expiration(Date.from(now.plus(Duration.ofDays(expireDays))))
            .signWith(secretKey)
            .compact();
    }

    public Long parseUserId(String token) {
        try {
            Claims claims = Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
            return Long.valueOf(claims.getSubject());
        } catch (Exception exception) {
            throw BusinessException.unauthorized("登录状态已过期，请重新登录");
        }
    }
}
