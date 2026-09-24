package com.prelude.llm.api;

/**
 * Text as a model actually returns it. Every caller that asks for structured JSON gets a
 * Markdown-fenced body back even when the prompt forbids fences, so the unwrapping lives
 * here once instead of in each reader.
 */
public final class LlmResponseText {

    /**
     * Strips a surrounding code fence and leaves the JSON body. A fence-less response is
     * returned trimmed and otherwise unchanged.
     */
    public static String stripJsonFence(String content) {
        String trimmed = content.trim();
        if (trimmed.startsWith("```json")) {
            trimmed = trimmed.substring(7);
        } else if (trimmed.startsWith("```")) {
            trimmed = trimmed.substring(3);
        }
        if (trimmed.endsWith("```")) {
            trimmed = trimmed.substring(0, trimmed.length() - 3);
        }
        return trimmed.trim();
    }

    private LlmResponseText() {
    }
}
