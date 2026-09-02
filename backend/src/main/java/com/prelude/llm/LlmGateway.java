package com.prelude.llm;

import com.prelude.llm.api.LlmPort;
import com.prelude.llm.api.ModelExecutionSnapshotRef;
import com.prelude.llm.api.SaveConfigurationCommand;
import com.prelude.llm.api.ModelConfigurationView;
import com.prelude.llm.api.ProviderDescriptorView;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * The LlmPort implementation: a thin coordinator delegating to the snapshot
 * service (freezing), the execution service (Spring AI runtime with single-
 * owner retry and profile-scoped fallback) and the profile service (BYOK
 * credentials and per-account configuration).
 */
@Service
@RequiredArgsConstructor
public class LlmGateway implements LlmPort {

    private final ModelExecutionSnapshotService snapshotService;
    private final ModelExecutionService executionService;
    private final ModelProfileService profileService;

    @Override
    public ModelExecutionSnapshotRef freezeSnapshot(FreezeSnapshotCommand command) {
        return snapshotService.freeze(command);
    }

    @Override
    public CompletionResult complete(ModelExecutionRequest request) {
        return executionService.complete(request);
    }

    @Override
    public void stream(ModelExecutionRequest request, StreamSink sink) {
        executionService.stream(request, sink);
    }

    @Override
    public ModelConfigurationView currentConfiguration(Long accountId) {
        return profileService.currentConfiguration(accountId);
    }

    @Override
    public ModelConfigurationView saveConfiguration(Long accountId, SaveConfigurationCommand command) {
        return profileService.saveConfiguration(accountId, command);
    }

    @Override
    public List<ProviderDescriptorView> listModels(Long accountId) {
        return profileService.listModels(accountId);
    }

    @Override
    public DiscoveredModelsView discoverCustomModels(Long accountId, DiscoverModelsCommand command) {
        return profileService.discoverCustomModels(accountId, command);
    }

    @Override
    public com.prelude.llm.api.ModelCapabilityResponse discoverCustomModelCapability(
        Long accountId,
        DiscoverModelCapabilityCommand command
    ) {
        return profileService.discoverCustomModelCapability(accountId, command);
    }
}
