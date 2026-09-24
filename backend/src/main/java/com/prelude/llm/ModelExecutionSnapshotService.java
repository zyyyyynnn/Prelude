package com.prelude.llm;

import com.prelude.BusinessException;
import com.prelude.llm.api.LlmPort.FreezeSnapshotCommand;
import com.prelude.llm.api.LlmPort.FrozenModelConfiguration;
import com.prelude.llm.api.ModelCapabilityResponse;
import com.prelude.llm.api.ModelExecutionSnapshotRef;
import com.prelude.llm.application.port.ModelExecutionSnapshotStore;
import com.prelude.llm.application.port.ModelExecutionSnapshotStore.SnapshotRow;
import com.prelude.llm.application.port.ModelProfileStore;
import com.prelude.llm.application.port.ModelProfileStore.ProfileRow;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

/**
 * Freezes immutable model execution snapshots. A run reads its configuration
 * only from the snapshot; later profile mutations affect the next snapshot,
 * never a running run.
 */
@Service
@RequiredArgsConstructor
public class ModelExecutionSnapshotService {

    private final ModelProfileStore profileStore;
    private final ModelExecutionSnapshotStore snapshotStore;
    private final ModelCapabilityCatalog capabilityCatalog;
    private final ReasoningLevels reasoningLevels;
    private final ModelCapabilityJson capabilityJson;
    private final ObjectMapper objectMapper;

    @Transactional(rollbackFor = Exception.class)
    public ModelExecutionSnapshotRef freeze(FreezeSnapshotCommand command) {
        ProfileRow profile = ProfileCapabilities.requireProfile(profileStore, command.accountId());
        String model = command.requestedModel() == null || command.requestedModel().isBlank()
            ? profile.model()
            : command.requestedModel().trim();
        String provider = profile.provider();
        ModelCapabilityResponse capability = ProfileCapabilities.capabilityForProfile(
            profile, model, capabilityCatalog, capabilityJson);

        var level = reasoningLevels.parse(command.reasoningLevel() == null
            ? profile.reasoningLevel()
            : command.reasoningLevel());
        if (!capability.supportedReasoningLevels().contains(level)) {
            throw BusinessException.badRequest("所选模型不支持该思考深度");
        }
        List<ModelCapabilityResponse> fallbackCapabilities = capabilityJson.readList(
            profile.fallbackCapabilitiesJson());
        validateFrozenFallbacks(provider, fallbackCapabilities, level);

        ModelExecutionParameters executionParameters = ModelExecutionParameters.fromProfileJson(
            profile.effectiveParametersJson(), objectMapper);
        Long snapshotId = snapshotStore.insert(new SnapshotRow(
            null,
            command.accountId(),
            profile.id(),
            provider,
            model,
            level.name(),
            executionParameters.toJson(objectMapper),
            ModelCapabilityCatalog.CAPABILITY_VERSION,
            capabilityJson.write(capability),
            capabilityJson.writeList(fallbackCapabilities),
            profile.credentialId(),
            profile.customEndpointUrl()
        ));
        return new ModelExecutionSnapshotRef(snapshotId);
    }

    /**
     * The frozen snapshot an execution run reads. Deliberately returns the store row: the
     * execution path copies and re-models it per fallback candidate, and the model factory is
     * built against that shape.
     */
    public SnapshotRow require(Long snapshotId) {
        SnapshotRow snapshot = snapshotStore.findById(snapshotId);
        if (snapshot == null) {
            throw BusinessException.modelSnapshotNotFound("模型执行快照不存在");
        }
        return snapshot;
    }

    public FrozenModelConfiguration frozenConfiguration(Long accountId, Long snapshotId) {
        SnapshotRow snapshot = require(snapshotId);
        if (!accountId.equals(snapshot.accountId())) {
            throw BusinessException.modelSnapshotNotFound("模型执行快照不存在");
        }
        return new FrozenModelConfiguration(snapshot.model(), snapshot.reasoningLevel());
    }

    private void validateFrozenFallbacks(
        String provider,
        List<ModelCapabilityResponse> fallbackCapabilities,
        ModelCapabilityResponse.ReasoningLevel reasoningLevel
    ) {
        for (ModelCapabilityResponse fallback : fallbackCapabilities) {
            if (!provider.equals(fallback.provider())) {
                throw BusinessException.badRequest("回退模型不能跨 Provider 边界");
            }
            if (!fallback.supportedReasoningLevels().contains(reasoningLevel)) {
                throw BusinessException.badRequest("回退模型不支持所选思考深度");
            }
        }
    }
}
