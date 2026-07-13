package com.interview.platform.llm.api;

import java.util.List;

public record LlmModelDiscoveryResponse(
    String providerKey,
    String baseUrl,
    List<String> models
) {
}
