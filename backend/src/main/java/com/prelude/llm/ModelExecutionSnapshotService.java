package com.prelude.llm;

import com.prelude.llm.api.LlmPort.FreezeSnapshotCommand;
import com.prelude.llm.api.ModelExecutionSnapshotRef;
import com.prelude.llm.persistence.ModelExecutionSnapshot;
import com.prelude.llm.persistence.ModelExecutionSnapshotMapper;
import com.prelude.llm.persistence.ModelProfile;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
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

    private final ModelProfileService modelProfileService;
    private final ModelExecutionSnapshotMapper snapshotMapper;
    private final ModelCapabilityCatalog capabilityCatalog;
    private final ReasoningLevels reasoningLevels;
    private final ObjectMapper objectMapper;

    @Transactional(rollbackFor = Exception.class)
    public ModelExecutionSnapshotRef freeze(FreezeSnapshotCommand command) {
        ModelProfile profile = modelProfileService.requireProfile(command.accountId());
        String model = command.requestedModel() == null || command.requestedModel().isBlank()
            ? profile.getModel()
            : command.requestedModel().trim();
        String provider = profile.getProvider();
        capabilityCatalog.requireSupportedModel(provider, model);

        var level = reasoningLevels.parse(command.reasoningLevel() == null
            ? profile.getReasoningLevel()
            : command.reasoningLevel());
        if (!modelProfileService.capabilityForProfile(profile, model).supportedReasoningLevels().contains(level)) {
            throw com.prelude.BusinessException.badRequest("所选模型不支持该思考深度");
        }
        validateFrozenFallbacks(provider, profile.getFallbackModelsJson(), level);

        ModelExecutionSnapshot snapshot = new ModelExecutionSnapshot();
        snapshot.setAccountId(command.accountId());
        snapshot.setProfileId(profile.getId());
        snapshot.setProvider(provider);
        snapshot.setModel(model);
        snapshot.setReasoningLevel(level.name());
        ModelExecutionParameters executionParameters = ModelExecutionParameters.fromProfileJson(
            profile.getEffectiveParametersJson(), objectMapper);
        snapshot.setEffectiveParametersJson(executionParameters.toJson(objectMapper));
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

    private void validateFrozenFallbacks(
        String provider,
        String fallbackModelsJson,
        com.prelude.llm.api.ModelCapabilityResponse.ReasoningLevel reasoningLevel
    ) {
        if (fallbackModelsJson == null || fallbackModelsJson.isBlank()
            || "[]".equals(fallbackModelsJson.trim())) {
            return;
        }
        final List<String> fallbackModels;
        try {
            fallbackModels = objectMapper.readValue(
                fallbackModelsJson, new TypeReference<List<String>>() {
                });
        } catch (Exception exception) {
            throw com.prelude.BusinessException.badRequest("模型回退配置无效，请重新保存模型配置");
        }
        for (String fallbackModel : fallbackModels) {
            capabilityCatalog.requireSupportedModel(provider, fallbackModel);
            if (!capabilityCatalog.capability(provider, fallbackModel)
                .supportedReasoningLevels().contains(reasoningLevel)) {
                throw com.prelude.BusinessException.badRequest("回退模型不支持所选思考深度");
            }
        }
    }
}
