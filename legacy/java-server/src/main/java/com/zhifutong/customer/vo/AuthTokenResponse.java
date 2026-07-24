package com.zhifutong.customer.vo;

public record AuthTokenResponse(
        String accessToken,
        long expiresIn,
        AuthUserResponse user
) {
}
