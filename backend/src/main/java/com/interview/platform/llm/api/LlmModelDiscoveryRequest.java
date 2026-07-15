package com.interview.platform.llm.api;

import jakarta.validation.constraints.NotBlank;

public record LlmModelDiscoveryRequest(
    @NotBlank(message = "providerKey 不能为空")
    String providerKey,

    @NotBlank(message = "baseUrl 不能为空")
    String baseUrl,

    String apiKey
) {
}
