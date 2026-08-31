package com.prelude.llm.api;

import java.util.List;

/**
 * One selectable model with its capability metadata.
 */
public record ModelDescriptorView(
    String provider,
    String displayName,
    String model,
    boolean customEndpoint,
    List<String> models
) {
}
