package com.interview.dto;

import jakarta.validation.constraints.NotBlank;

public record UserLlmConfigRequest(
    @NotBlank(message = "providerKey 不能为空")
    String providerKey,

    @NotBlank(message = "model 不能为空")
    String model,

    String apiKey
) {
}
