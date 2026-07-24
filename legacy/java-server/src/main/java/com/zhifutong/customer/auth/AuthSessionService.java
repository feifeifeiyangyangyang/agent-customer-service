package com.zhifutong.customer.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Base64;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class AuthSessionService {
    private static final String REFRESH_PREFIX = "auth:refresh:";
    private static final String BLACKLIST_PREFIX = "auth:blacklist:";
    private final StringRedisTemplate redisTemplate;

    public AuthSessionService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void storeRefreshToken(Long userId, String tokenId, String refreshToken, Duration ttl) {
        redisTemplate.opsForValue().set(refreshKey(userId, tokenId), digest(refreshToken), ttl);
    }

    public boolean refreshTokenMatches(Long userId, String tokenId, String refreshToken) {
        String expectedDigest = redisTemplate.opsForValue().get(refreshKey(userId, tokenId));
        return expectedDigest != null && expectedDigest.equals(digest(refreshToken));
    }

    public void revokeRefreshToken(Long userId, String tokenId) {
        redisTemplate.delete(refreshKey(userId, tokenId));
    }

    public void blacklistAccessToken(String tokenId, Duration ttl) {
        if (!ttl.isNegative() && !ttl.isZero()) {
            redisTemplate.opsForValue().set(BLACKLIST_PREFIX + tokenId, "1", ttl);
        }
    }

    public boolean accessTokenBlacklisted(String tokenId) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(BLACKLIST_PREFIX + tokenId));
    }

    private String refreshKey(Long userId, String tokenId) {
        return REFRESH_PREFIX + userId + ":" + tokenId;
    }

    private String digest(String token) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (Exception ex) {
            throw new IllegalStateException("Refresh token digest failed", ex);
        }
    }
}
