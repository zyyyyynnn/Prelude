package com.interview.shared.web;

public final class UserContext {

    private static final ThreadLocal<Long> CURRENT_USER = new ThreadLocal<>();
    private static final ThreadLocal<Long> CURRENT_SESSION = new ThreadLocal<>();

    private UserContext() {
    }

    public static void setCurrentUserId(Long userId) {
        CURRENT_USER.set(userId);
    }

    public static Long getCurrentUserId() {
        return CURRENT_USER.get();
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
