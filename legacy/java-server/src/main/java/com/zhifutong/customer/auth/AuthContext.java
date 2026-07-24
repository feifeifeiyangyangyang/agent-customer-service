package com.zhifutong.customer.auth;

import com.zhifutong.customer.exception.BusinessException;
import org.springframework.http.HttpStatus;

public final class AuthContext {
    private static final ThreadLocal<AuthenticatedUser> HOLDER = new ThreadLocal<>();

    private AuthContext() {
    }

    public static void set(AuthenticatedUser user) {
        HOLDER.set(user);
    }

    public static AuthenticatedUser require() {
        AuthenticatedUser user = HOLDER.get();
        if (user == null) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "请先登录");
        }
        return user;
    }

    public static void clear() {
        HOLDER.remove();
    }
}
