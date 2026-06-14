package com.interview.dto;

public record UserLlmConfigResponse(
    String providerKey,
    String baseUrl,
    String model,
    Boolean hasApiKey,
    String apiKeyMasked,
    Integer maxTokens,
    String thinkingDepth
) {
}
