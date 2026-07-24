package com.zhifutong.customer.vo;

import com.zhifutong.customer.domain.UserRole;

public record AuthUserResponse(
        Long userId,
        String username,
        String name,
        UserRole role
) {
}
