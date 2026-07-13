package com.interview.platform.llm.api;

import jakarta.validation.constraints.NotBlank;

public record UserLlmConfigRequest(
    @NotBlank(message = "providerKey 不能为空")
    String providerKey,

    String baseUrl,

    @NotBlank(message = "model 不能为空")
    String model,

    String apiKey,

    Integer maxTokens,

    String thinkingDepth
) {
}
