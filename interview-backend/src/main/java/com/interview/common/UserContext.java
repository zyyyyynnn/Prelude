package com.interview.common;

public final class UserContext {

    private static final ThreadLocal<Long> CURRENT_USER = new ThreadLocal<>();

    private UserContext() {
    }

    public static void setCurrentUserId(Long userId) {
        CURRENT_USER.set(userId);
    }

    public static Long getCurrentUserId() {
        return CURRENT_USER.get();
    }

    public static void remove() {
        CURRENT_USER.remove();
    }
}
