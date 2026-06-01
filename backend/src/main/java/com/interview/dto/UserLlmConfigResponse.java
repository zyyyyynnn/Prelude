package com.interview.dto;

public record UserLlmConfigResponse(
    String providerKey,
    String model,
    String apiKeyMasked,
    Integer maxTokens,
    Integer thinkingDepth
) {
}
