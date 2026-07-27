package com.designforge.api.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.UUID;

@Component
public class RefreshTokenStore {

    private final StringRedisTemplate redisTemplate;
    private final long refreshTokenTtlDays;

    public RefreshTokenStore(
            StringRedisTemplate redisTemplate,
            @Value("${jwt.refresh-token-ttl-days:7}") long refreshTokenTtlDays
    ) {
        this.redisTemplate = redisTemplate;
        this.refreshTokenTtlDays = refreshTokenTtlDays;
    }

    private String key(UUID userId) {
        return "refresh:" + userId;
    }

    public void store(UUID userId, String refreshToken) {
        redisTemplate.opsForValue().set(key(userId), refreshToken, Duration.ofDays(refreshTokenTtlDays));
    }

    public boolean isValid(UUID userId, String refreshToken) {
        String stored = redisTemplate.opsForValue().get(key(userId));
        return stored != null && stored.equals(refreshToken);
    }

    public void revoke(UUID userId) {
        redisTemplate.delete(key(userId));
    }
}
