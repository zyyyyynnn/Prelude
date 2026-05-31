package com.interview.dto;

import java.util.List;

public record LlmProviderResponse(
    String providerKey,
    String displayName,
    List<String> availableModels,
    Integer enabled
) {
}
