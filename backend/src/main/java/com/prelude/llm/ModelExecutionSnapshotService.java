package com.prelude.llm;

import com.prelude.llm.api.LlmPort.FreezeSnapshotCommand;
import com.prelude.llm.api.ModelExecutionSnapshotRef;
import com.prelude.llm.persistence.ModelExecutionSnapshot;
import com.prelude.llm.persistence.ModelExecutionSnapshotMapper;
import com.prelude.llm.persistence.ModelProfile;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Freezes immutable model execution snapshots. A run reads its configuration
 * only from the snapshot; later profile mutations affect the next snapshot,
 * never a running run.
 */
@Service
@RequiredArgsConstructor
public class ModelExecutionSnapshotService {

    private final ModelProfileService modelProfileService;
    private final ModelExecutionSnapshotMapper snapshotMapper;
    private final ModelCapabilityCatalog capabilityCatalog;
    private final ReasoningLevels reasoningLevels;

    @Transactional(rollbackFor = Exception.class)
    public ModelExecutionSnapshotRef freeze(FreezeSnapshotCommand command) {
        ModelProfile profile = modelProfileService.requireProfile(command.accountId());
        String model = command.requestedModel() == null || command.requestedModel().isBlank()
            ? profile.getModel()
            : command.requestedModel().trim();
        String provider = profile.getProvider();

        var level = reasoningLevels.parse(command.reasoningLevel() == null
            ? profile.getReasoningLevel()
            : command.reasoningLevel());
        if (!capabilityCatalog.capability(provider, model).supportedReasoningLevels().contains(level)) {
            level = com.prelude.llm.api.ModelCapabilityResponse.ReasoningLevel.AUTO;
        }

        ModelExecutionSnapshot snapshot = new ModelExecutionSnapshot();
        snapshot.setAccountId(command.accountId());
        snapshot.setProfileId(profile.getId());
        snapshot.setProvider(provider);
        snapshot.setModel(model);
        snapshot.setReasoningLevel(level.name());
        snapshot.setEffectiveParametersJson(profile.getEffectiveParametersJson() == null
            ? "{}"
            : profile.getEffectiveParametersJson());
        snapshot.setCapabilityVersion(ModelCapabilityCatalog.CAPABILITY_VERSION);
        snapshot.setFallbackModelsJson(profile.getFallbackModelsJson() == null
            ? "[]"
            : profile.getFallbackModelsJson());
        snapshot.setCredentialId(profile.getCredentialId());
        snapshot.setCustomEndpointUrl(profile.getCustomEndpointUrl());
        snapshotMapper.insert(snapshot);
        return new ModelExecutionSnapshotRef(snapshot.getId());
    }

    public ModelExecutionSnapshot require(Long snapshotId) {
        ModelExecutionSnapshot snapshot = snapshotMapper.selectById(snapshotId);
        if (snapshot == null) {
            throw new com.prelude.BusinessException(
                org.springframework.http.HttpStatus.NOT_FOUND, "model_snapshot_not_found", "模型执行快照不存在");
        }
        return snapshot;
    }
}
