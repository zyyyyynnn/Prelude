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

    List<ModelDescriptorView> listModels(Long accountId);

    DiscoveredModelsView discoverCustomModels(Long accountId, DiscoverModelsCommand command);

    record FreezeSnapshotCommand(
        Long accountId,
        String provider,
        String model,
        String reasoningLevel,
        String requestedModel
    ) {
    }

    record ModelExecutionRequest(
        Long snapshotId,
        String purpose,
        String promptId,
        List<Message> messages,
        List<Attachment> attachments
    ) {
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

    record DiscoverModelsCommand(String baseUrl, String apiKey) {
    }

    record DiscoveredModelsView(String baseUrl, List<String> models) {
    }
}
