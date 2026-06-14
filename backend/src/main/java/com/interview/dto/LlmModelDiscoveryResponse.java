package com.interview.dto;

import java.util.List;

public record LlmModelDiscoveryResponse(
    String providerKey,
    String baseUrl,
    List<String> models
) {
}
