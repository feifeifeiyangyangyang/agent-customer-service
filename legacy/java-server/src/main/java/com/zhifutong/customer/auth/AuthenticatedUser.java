package com.zhifutong.customer.auth;

import com.zhifutong.customer.domain.UserRole;

public record AuthenticatedUser(
        Long userId,
        String username,
        String name,
        UserRole role,
        String tokenId
) {
    public boolean isAdmin() {
        return role == UserRole.ADMIN;
    }
}
