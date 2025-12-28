package com.example.chat.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class RedisBaseService {

    private final RedisTemplate<String, Object> redisTemplate;

    private static final String BLACKLIST_PREFIX = "jwt:blacklist:";

    public void set(String key, Object value) {
        redisTemplate.opsForValue().set(key, value);
    }

    public void setWithTTL(String key, Object value, long minutes) {
        redisTemplate.opsForValue().set(key, value, minutes, TimeUnit.MINUTES);
    }

    public Object get(String key) {
        return redisTemplate.opsForValue().get(key);
    }

    public void delete(String key) {
        redisTemplate.delete(key);
    }

    /* ================= JWT BLACKLIST ================= */

    /**
     * Blacklist access token với TTL (milliseconds)
     */
    public void blacklistToken(String token, long ttlMillis) {
        if (ttlMillis <= 0) return;

        String key = BLACKLIST_PREFIX + token;
        redisTemplate.opsForValue().set(key, "BLACKLISTED", ttlMillis, TimeUnit.MILLISECONDS);
    }

    /**
     * Check token có nằm trong blacklist không
     */
    public boolean isTokenBlacklisted(String token) {
        return redisTemplate.hasKey(BLACKLIST_PREFIX + token);
    }
}