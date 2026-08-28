package com.prelude;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public final class UserContext {

    private static final ThreadLocal<Long> CURRENT_USER = new ThreadLocal<>();
    private static final ThreadLocal<Long> CURRENT_SESSION = new ThreadLocal<>();

    private UserContext() {
    }

    public static void setCurrentUserId(Long userId) {
        CURRENT_USER.set(userId);
    }

    public static Long getCurrentUserId() {
        Long explicitUserId = CURRENT_USER.get();
        if (explicitUserId != null) {
            return explicitUserId;
        }
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof Long userId) {
            return userId;
        }
        if (principal instanceof String value) {
            try {
                return Long.valueOf(value);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    public static void setCurrentSessionId(Long sessionId) {
        CURRENT_SESSION.set(sessionId);
    }

    public static Long getCurrentSessionId() {
        return CURRENT_SESSION.get();
    }

    public static void remove() {
        CURRENT_USER.remove();
        CURRENT_SESSION.remove();
    }
}
