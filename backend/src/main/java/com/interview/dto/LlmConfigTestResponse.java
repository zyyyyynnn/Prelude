package com.interview.dto;

public record LlmConfigTestResponse(
    String providerKey,
    String model,
    boolean ok,
    String message
) {
}
