package com.prelude.llm;

import com.prelude.llm.api.ModelCapabilityResponse;
import com.prelude.llm.api.ModelConfigurationPort;
import com.prelude.llm.api.ModelConfigurationView;
import com.prelude.llm.api.ModelDiscoveryPort;
import com.prelude.llm.api.ModelExecutionPort;
import com.prelude.llm.api.ModelExecutionPort.CompletionResult;
import com.prelude.llm.api.ModelExecutionPort.FreezeSnapshotCommand;
import com.prelude.llm.api.ModelExecutionPort.ModelExecutionRequest;
import com.prelude.llm.api.ModelExecutionPort.StreamSink;
import com.prelude.llm.api.ModelExecutionSnapshotRef;
import com.prelude.llm.api.ProviderDescriptorView;
import com.prelude.llm.api.SaveConfigurationCommand;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ModelExecutionAdapter implements
    ModelExecutionPort, ModelConfigurationPort, ModelDiscoveryPort {

    private final ModelExecutionSnapshotService snapshotService;
    private final ModelExecutionService executionService;
    private final ModelProfileService profileService;

    @Override
    public ModelExecutionSnapshotRef freezeSnapshot(FreezeSnapshotCommand command) {
        return snapshotService.freeze(command);
    }

    @Override
    public ModelExecutionPort.FrozenModelConfiguration frozenConfiguration(Long accountId, Long snapshotId) {
        return snapshotService.frozenConfiguration(accountId, snapshotId);
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
    public List<ProviderDescriptorView> listModels() {
        return profileService.listModels();
    }

    @Override
    public ModelDiscoveryPort.DiscoveredModelsView discoverCustomModels(
        Long accountId,
        ModelDiscoveryPort.DiscoverModelsCommand command
    ) {
        return profileService.discoverCustomModels(accountId, command);
    }

    @Override
    public ModelCapabilityResponse discoverCustomModelCapability(
        Long accountId,
        ModelDiscoveryPort.DiscoverModelCapabilityCommand command
    ) {
        return profileService.discoverCustomModelCapability(accountId, command);
    }
}
