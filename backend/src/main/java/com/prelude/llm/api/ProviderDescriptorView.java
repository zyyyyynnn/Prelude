package com.prelude.llm.api;

import java.util.List;

/**
 * Canonical provider projection consumed by settings and interview composer.
 * Every built-in model carries its code-backed capability truth; custom
 * endpoint models are populated by the discovery response.
 */
public record ProviderDescriptorView(
    String providerKey,
    String displayName,
    boolean customEndpoint,
    List<ModelCapabilityResponse> models
) {
}
