package com.zhifutong.customer.ratelimit;

import com.zhifutong.customer.config.AppProperties;
import com.zhifutong.customer.exception.BusinessException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class ChatRateLimiter {
    private static final DateTimeFormatter MINUTE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmm");
    private static final String LUA = """
            local current = redis.call('INCR', KEYS[1])
            if current == 1 then
                redis.call('EXPIRE', KEYS[1], ARGV[2])
            end
            return current
            """;

    private final StringRedisTemplate redisTemplate;
    private final AppProperties properties;
    private final DefaultRedisScript<Long> script;

    public ChatRateLimiter(StringRedisTemplate redisTemplate, AppProperties properties) {
        this.redisTemplate = redisTemplate;
        this.properties = properties;
        this.script = new DefaultRedisScript<>(LUA, Long.class);
    }

    public void check(Long userId) {
        int limit = properties.getRateLimit().getChatPerMinute();
        String key = "rate:chat:%d:%s".formatted(userId, LocalDateTime.now().format(MINUTE_FORMAT));
        Long count = redisTemplate.execute(script, List.of(key), String.valueOf(limit), "70");
        if (count != null && count > limit) {
            throw new BusinessException(HttpStatus.TOO_MANY_REQUESTS, "提问过于频繁，请稍后再试");
        }
    }
}
