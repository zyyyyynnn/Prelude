package com.prelude.llm;

/**
 * Correlation data of the current LLM invocation inside the llm module.
 * Carries only what the invocation path itself needs (e.g. session-scoped
 * realtime broadcasts); it never carries authorization identity — the
 * account id is always passed explicitly.
 */
public final class LlmInvocationContext {

    private static final ThreadLocal<Long> CURRENT_SESSION = new ThreadLocal<>();

    private LlmInvocationContext() {
    }

    public static void setCurrentSessionId(Long sessionId) {
        CURRENT_SESSION.set(sessionId);
    }

    public static Long getCurrentSessionId() {
        return CURRENT_SESSION.get();
    }

    public static void clear() {
        CURRENT_SESSION.remove();
    }
}
