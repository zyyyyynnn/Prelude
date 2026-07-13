package com.interview.platform.llm.api;

public record LlmConfigTestResponse(
    String providerKey,
    String model,
    boolean ok,
    String message
) {
}
