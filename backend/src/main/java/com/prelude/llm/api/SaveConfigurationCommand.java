package com.prelude.llm.api;

import java.util.List;

/**
 * Save the per-account model configuration. Falls under the same credential
 * boundary rules: a provider/endpoint scope change must re-provide the key.
 */
public record SaveConfigurationCommand(
    String provider,
    String model,
    String customEndpointUrl,
    String apiKey,
    String reasoningLevel,
    List<String> fallbackModels
) {

    public static final String CLEAR_API_KEY = "__CLEAR__";
}
