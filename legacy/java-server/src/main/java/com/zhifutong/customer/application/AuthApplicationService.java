package com.zhifutong.customer.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhifutong.customer.auth.AuthSessionService;
import com.zhifutong.customer.auth.AuthenticatedUser;
import com.zhifutong.customer.auth.JwtTokenService;
import com.zhifutong.customer.config.AppProperties;
import com.zhifutong.customer.domain.UserRole;
import com.zhifutong.customer.domain.UserStatus;
import com.zhifutong.customer.entity.UserAccount;
import com.zhifutong.customer.exception.BusinessException;
import com.zhifutong.customer.mapper.UserAccountMapper;
import com.zhifutong.customer.vo.AuthTokenResponse;
import com.zhifutong.customer.vo.AuthUserResponse;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthApplicationService {
    public static final String REFRESH_COOKIE = "refresh_token";

    private final UserAccountMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService tokenService;
    private final AuthSessionService sessionService;
    private final AppProperties properties;
    private final SecureRandom secureRandom = new SecureRandom();

    public AuthApplicationService(UserAccountMapper userMapper, PasswordEncoder passwordEncoder,
                                  JwtTokenService tokenService, AuthSessionService sessionService,
                                  AppProperties properties) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
        this.sessionService = sessionService;
        this.properties = properties;
    }

    @Transactional
    public AuthTokenIssue register(String username, String password, String displayName) {
        String cleanUsername = username.trim();
        if (findByUsername(cleanUsername) != null) {
            throw new BusinessException(HttpStatus.CONFLICT, "用户名已存在");
        }
        LocalDateTime now = LocalDateTime.now();
        UserAccount user = new UserAccount();
        user.setUsername(cleanUsername);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setDisplayName(displayName.trim());
        user.setRole(UserRole.CUSTOMER);
        user.setStatus(UserStatus.ACTIVE);
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        userMapper.insert(user);
        return issueTokens(user);
    }

    @Transactional
    public AuthTokenIssue login(String username, String password) {
        UserAccount user = findByUsername(username.trim());
        if (user == null || !passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "账号或密码不正确");
        }
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "账号已被禁用");
        }
        user.setLastLoginAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        userMapper.updateById(user);
        return issueTokens(user);
    }

    public AuthenticatedUser authenticateAccessToken(String accessToken) {
        AuthenticatedUser user = tokenService.verify(accessToken);
        if (sessionService.accessTokenBlacklisted(user.tokenId())) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "登录已失效，请重新登录");
        }
        UserAccount account = userMapper.selectById(user.userId());
        if (account == null || account.getStatus() != UserStatus.ACTIVE) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "登录账号不可用，请重新登录");
        }
        return user;
    }

    public AuthTokenIssue refresh(String refreshToken) {
        RefreshTokenParts parts = parseRefreshToken(refreshToken);
        if (!sessionService.refreshTokenMatches(parts.userId(), parts.tokenId(), refreshToken)) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "刷新凭证无效，请重新登录");
        }
        UserAccount user = userMapper.selectById(parts.userId());
        if (user == null || user.getStatus() != UserStatus.ACTIVE) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "登录账号不可用，请重新登录");
        }
        sessionService.revokeRefreshToken(parts.userId(), parts.tokenId());
        return issueTokens(user);
    }

    public void logout(AuthenticatedUser user, String accessToken, String refreshToken) {
        if (refreshToken != null && !refreshToken.isBlank()) {
            RefreshTokenParts parts = parseRefreshToken(refreshToken);
            sessionService.revokeRefreshToken(parts.userId(), parts.tokenId());
        }
        sessionService.blacklistAccessToken(user.tokenId(), tokenService.remainingTtl(accessToken));
    }

    private AuthTokenIssue issueTokens(UserAccount user) {
        String accessToken = tokenService.createToken(user.getId(), user.getUsername(), user.getDisplayName(), user.getRole());
        String refreshToken = createRefreshToken(user.getId());
        RefreshTokenParts parts = parseRefreshToken(refreshToken);
        Duration refreshTtl = Duration.ofDays(properties.getAuth().getRefreshTokenTtlDays());
        sessionService.storeRefreshToken(user.getId(), parts.tokenId(), refreshToken, refreshTtl);
        AuthUserResponse authUser = new AuthUserResponse(user.getId(), user.getUsername(), user.getDisplayName(), user.getRole());
        AuthTokenResponse response = new AuthTokenResponse(accessToken, tokenService.accessTtlSeconds(), authUser);
        return new AuthTokenIssue(response, refreshToken, refreshTtl);
    }

    private UserAccount findByUsername(String username) {
        return userMapper.selectOne(new LambdaQueryWrapper<UserAccount>().eq(UserAccount::getUsername, username));
    }

    private String createRefreshToken(Long userId) {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return userId + "." + UUID.randomUUID() + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private RefreshTokenParts parseRefreshToken(String refreshToken) {
        String[] parts = refreshToken == null ? new String[0] : refreshToken.split("\\.");
        if (parts.length != 3) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "刷新凭证无效，请重新登录");
        }
        try {
            return new RefreshTokenParts(Long.parseLong(parts[0]), parts[1]);
        } catch (NumberFormatException ex) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "刷新凭证无效，请重新登录");
        }
    }

    public record AuthTokenIssue(AuthTokenResponse response, String refreshToken, Duration refreshTtl) {
    }

    private record RefreshTokenParts(Long userId, String tokenId) {
    }
}
