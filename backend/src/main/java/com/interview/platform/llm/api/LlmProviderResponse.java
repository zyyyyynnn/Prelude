package com.interview.platform.llm.api;

import java.util.List;

public record LlmProviderResponse(
    String providerKey,
    String displayName,
    List<String> availableModels,
    Integer enabled
) {
}
