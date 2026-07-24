package com.zhifutong.customer.auth;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhifutong.customer.config.AppProperties;
import com.zhifutong.customer.domain.UserRole;
import com.zhifutong.customer.exception.BusinessException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class JwtTokenService {
    private static final Base64.Encoder URL_ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder URL_DECODER = Base64.getUrlDecoder();
    private final AppProperties properties;
    private final ObjectMapper objectMapper;

    public JwtTokenService(AppProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public String createToken(Long userId, String username, String name, UserRole role) {
        String tokenId = UUID.randomUUID().toString();
        long now = Instant.now().getEpochSecond();
        long exp = now + accessTtlSeconds();
        Map<String, Object> header = Map.of("alg", "HS256", "typ", "JWT");
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("jti", tokenId);
        payload.put("sub", userId);
        payload.put("username", username);
        payload.put("name", name);
        payload.put("role", role.name());
        payload.put("iat", now);
        payload.put("exp", exp);
        String unsigned = encodeJson(header) + "." + encodeJson(payload);
        return unsigned + "." + sign(unsigned);
    }

    public AuthenticatedUser verify(String token) {
        String[] parts = token == null ? new String[0] : token.split("\\.");
        if (parts.length != 3) {
            throw unauthorized();
        }
        String unsigned = parts[0] + "." + parts[1];
        if (!constantEquals(sign(unsigned), parts[2])) {
            throw unauthorized();
        }
        Map<String, Object> payload = decodePayload(parts[1]);
        long exp = numberValue(payload.get("exp"));
        if (Instant.now().getEpochSecond() >= exp) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "登录已过期，请重新登录");
        }
        Long userId = numberValue(payload.get("sub"));
        String role = String.valueOf(payload.get("role"));
        return new AuthenticatedUser(
                userId,
                String.valueOf(payload.get("username")),
                String.valueOf(payload.get("name")),
                UserRole.valueOf(role),
                String.valueOf(payload.get("jti"))
        );
    }

    public long accessTtlSeconds() {
        return properties.getAuth().getAccessTokenTtlMinutes() * 60;
    }

    public Duration remainingTtl(String token) {
        String[] parts = token == null ? new String[0] : token.split("\\.");
        if (parts.length != 3) {
            return Duration.ZERO;
        }
        Map<String, Object> payload = decodePayload(parts[1]);
        long remaining = numberValue(payload.get("exp")) - Instant.now().getEpochSecond();
        return remaining <= 0 ? Duration.ZERO : Duration.ofSeconds(remaining);
    }

    public String tokenId(String token) {
        String[] parts = token == null ? new String[0] : token.split("\\.");
        if (parts.length != 3) {
            throw unauthorized();
        }
        return String.valueOf(decodePayload(parts[1]).get("jti"));
    }

    private String encodeJson(Object value) {
        try {
            return URL_ENCODER.encodeToString(objectMapper.writeValueAsBytes(value));
        } catch (JsonProcessingException ex) {
            throw new BusinessException("登录凭证生成失败");
        }
    }

    private Map<String, Object> decodePayload(String encoded) {
        try {
            return objectMapper.readValue(URL_DECODER.decode(encoded), new TypeReference<>() {});
        } catch (Exception ex) {
            throw unauthorized();
        }
    }

    private String sign(String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(properties.getAuth().getJwtSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return URL_ENCODER.encodeToString(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new BusinessException("登录凭证签名失败");
        }
    }

    private boolean constantEquals(String left, String right) {
        return java.security.MessageDigest.isEqual(left.getBytes(StandardCharsets.UTF_8), right.getBytes(StandardCharsets.UTF_8));
    }

    private long numberValue(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(String.valueOf(value));
    }

    private BusinessException unauthorized() {
        return new BusinessException(HttpStatus.UNAUTHORIZED, "登录状态无效，请重新登录");
    }
}
