package com.prelude.llm.api;

import java.util.List;

/**
 * The llm module's public boundary for model execution. Runs execute against
 * immutable ModelExecutionSnapshots; mutable profiles only affect the next
 * snapshot, never a running one.
 */
public interface LlmPort {

    ModelExecutionSnapshotRef freezeSnapshot(FreezeSnapshotCommand command);

    CompletionResult complete(ModelExecutionRequest request);

    void stream(ModelExecutionRequest request, StreamSink sink);

    ModelConfigurationView currentConfiguration(Long accountId);

    ModelConfigurationView saveConfiguration(Long accountId, SaveConfigurationCommand command);

    List<ProviderDescriptorView> listModels(Long accountId);

    DiscoveredModelsView discoverCustomModels(Long accountId, DiscoverModelsCommand command);

    ModelCapabilityResponse discoverCustomModelCapability(
        Long accountId,
        DiscoverModelCapabilityCommand command
    );

    record FreezeSnapshotCommand(
        Long accountId,
        String reasoningLevel,
        String requestedModel
    ) {
    }

    record ModelExecutionRequest(
        Long snapshotId,
        String purpose,
        String promptId,
        ResponseMode responseMode,
        List<Message> messages,
        List<Attachment> attachments,
        List<ToolBinding> tools
    ) {
    }

    enum ResponseMode {
        PLAIN_TEXT,
        JSON_OBJECT,
        JSON_ARRAY
    }

    record ToolBinding(
        String name,
        String description,
        String inputSchema,
        ToolHandler handler
    ) {
    }

    @FunctionalInterface
    interface ToolHandler {
        String call(String argumentsJson);
    }

    record Message(String role, String content) {
    }

    /**
     * Multimodal attachment for genuine image consumers. Content stays inside
     * the llm module boundary; callers only reference it.
     */
    record Attachment(String fileName, String mediaType, byte[] content) {
    }

    interface StreamSink {

        void onNext(String delta);

        void onUsage(Usage usage);
    }

    record CompletionResult(String content, Usage usage) {
    }

    /**
     * Usage handoff for #46 telemetry: correlation + token accounting. Values
     * come from provider responses; nothing is fabricated.
     */
    record Usage(
        Long snapshotId,
        String purpose,
        String provider,
        String model,
        Long inputTokens,
        Long outputTokens,
        Long totalTokens
    ) {
    }

    record DiscoverModelsCommand(String provider, String baseUrl, String apiKey) {
    }

    record DiscoverModelCapabilityCommand(String provider, String baseUrl, String apiKey, String model) {
    }

    record DiscoveredModelsView(String baseUrl, List<ModelCapabilityResponse> models) {
    }
}
