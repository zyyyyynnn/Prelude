package com.prelude.llm.api;

import java.util.List;

/** Discovers provider models and capabilities without changing account configuration. */
public interface ModelDiscoveryPort {

    List<ProviderDescriptorView> listModels();

    DiscoveredModelsView discoverCustomModels(
        Long accountId,
        DiscoverModelsCommand command
    );

    ModelCapabilityResponse discoverCustomModelCapability(
        Long accountId,
        DiscoverModelCapabilityCommand command
    );

    record DiscoverModelsCommand(String provider, String baseUrl, String apiKey) {
    }

    record DiscoverModelCapabilityCommand(String provider, String baseUrl, String apiKey, String model) {
    }

    record DiscoveredModelsView(String baseUrl, List<ModelCapabilityResponse> models) {
    }
}
