package com.zhifutong.customer.controller;

import com.zhifutong.customer.application.AuthApplicationService;
import com.zhifutong.customer.application.AuthApplicationService.AuthTokenIssue;
import com.zhifutong.customer.auth.AuthContext;
import com.zhifutong.customer.auth.AuthenticatedUser;
import com.zhifutong.customer.dto.LoginRequest;
import com.zhifutong.customer.dto.RegisterRequest;
import com.zhifutong.customer.vo.ApiResponse;
import com.zhifutong.customer.vo.AuthTokenResponse;
import com.zhifutong.customer.vo.AuthUserResponse;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.time.Duration;
import java.util.Arrays;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final AuthApplicationService authService;

    public AuthController(AuthApplicationService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public ApiResponse<AuthTokenResponse> register(@Valid @RequestBody RegisterRequest request, HttpServletResponse response) {
        return issue(authService.register(request.username(), request.password(), request.displayName()), response);
    }

    @PostMapping("/login")
    public ApiResponse<AuthTokenResponse> login(@Valid @RequestBody LoginRequest request, HttpServletResponse response) {
        return issue(authService.login(request.username(), request.password()), response);
    }

    @PostMapping("/refresh")
    public ApiResponse<AuthTokenResponse> refresh(@CookieValue(value = AuthApplicationService.REFRESH_COOKIE, required = false) String refreshToken,
                                                  HttpServletResponse response) {
        return issue(authService.refresh(refreshToken), response);
    }

    @GetMapping("/me")
    public ApiResponse<AuthUserResponse> me() {
        AuthenticatedUser user = AuthContext.require();
        return ApiResponse.ok(new AuthUserResponse(user.userId(), user.username(), user.name(), user.role()));
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(HttpServletRequest request, HttpServletResponse response,
                                    @CookieValue(value = AuthApplicationService.REFRESH_COOKIE, required = false) String refreshToken) {
        String accessToken = bearerToken(request);
        authService.logout(AuthContext.require(), accessToken, refreshToken);
        response.addHeader(HttpHeaders.SET_COOKIE, refreshCookie("", Duration.ZERO).toString());
        return ApiResponse.ok(null);
    }

    private ApiResponse<AuthTokenResponse> issue(AuthTokenIssue issue, HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE, refreshCookie(issue.refreshToken(), issue.refreshTtl()).toString());
        return ApiResponse.ok(issue.response());
    }

    private ResponseCookie refreshCookie(String value, Duration ttl) {
        return ResponseCookie.from(AuthApplicationService.REFRESH_COOKIE, value)
                .httpOnly(true)
                .secure(false)
                .sameSite("Lax")
                .path("/api/v1/auth")
                .maxAge(ttl)
                .build();
    }

    private String bearerToken(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        return header != null && header.startsWith("Bearer ") ? header.substring("Bearer ".length()).trim() : "";
    }
}
