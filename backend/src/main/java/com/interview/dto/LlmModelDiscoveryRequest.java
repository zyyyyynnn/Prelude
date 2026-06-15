package com.interview.dto;

import jakarta.validation.constraints.NotBlank;

public record LlmModelDiscoveryRequest(
    @NotBlank(message = "baseUrl 不能为空")
    String baseUrl,

    String apiKey
) {
}
