package com.prelude.llm.api;

import java.util.List;

/**
 * Current per-account model configuration projection for settings and the
 * composer. Reasoning levels are capability-driven: only levels the selected
 * provider/model actually support are exposed.
 */
public record ModelConfigurationView(
    String provider,
    String model,
    String customEndpointUrl,
    boolean hasApiKey,
    String apiKeyMasked,
    String reasoningLevel,
    List<String> fallbackModels,
    ModelCapabilityResponse capability
) {
}
