package com.framelab.common.security;

import java.util.Optional;

import com.framelab.common.error.ApiException;
import com.framelab.common.error.ErrorCode;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public final class SecurityUtils {

    private SecurityUtils() {
    }

    public static Optional<AuthUser> currentUserOptional() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof AuthUser authUser) {
            return Optional.of(authUser);
        }
        return Optional.empty();
    }

    public static AuthUser currentUser() {
        return currentUserOptional().orElseThrow(() -> ApiException.of(ErrorCode.AUTH_REQUIRED));
    }
}
