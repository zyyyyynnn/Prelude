package com.prelude.llm.api;

public record LlmConfigTestResponse(
    String providerKey,
    String model,
    boolean ok,
    String message
) {
}
