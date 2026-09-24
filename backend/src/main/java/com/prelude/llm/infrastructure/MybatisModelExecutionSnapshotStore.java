package com.prelude.llm.infrastructure;

import com.prelude.llm.application.port.ModelExecutionSnapshotStore;
import com.prelude.llm.application.port.ModelExecutionSnapshotStore.SnapshotRow;
import com.prelude.llm.infrastructure.persistence.ModelExecutionSnapshotEntity;
import com.prelude.llm.infrastructure.persistence.ModelExecutionSnapshotMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/** MyBatis-Plus adapter for {@link ModelExecutionSnapshotStore}. */
@Repository
@RequiredArgsConstructor
public class MybatisModelExecutionSnapshotStore implements ModelExecutionSnapshotStore {

    private final ModelExecutionSnapshotMapper snapshotMapper;

    @Override
    public Long insert(SnapshotRow row) {
        ModelExecutionSnapshotEntity snapshot = new ModelExecutionSnapshotEntity();
        snapshot.setAccountId(row.accountId());
        snapshot.setProfileId(row.profileId());
        snapshot.setProvider(row.provider());
        snapshot.setModel(row.model());
        snapshot.setReasoningLevel(row.reasoningLevel());
        snapshot.setEffectiveParametersJson(row.effectiveParametersJson());
        snapshot.setCapabilityVersion(row.capabilityVersion());
        snapshot.setModelCapabilityJson(row.modelCapabilityJson());
        snapshot.setFallbackCapabilitiesJson(row.fallbackCapabilitiesJson());
        snapshot.setCredentialId(row.credentialId());
        snapshot.setCustomEndpointUrl(row.customEndpointUrl());
        snapshotMapper.insert(snapshot);
        return snapshot.getId();
    }

    @Override
    public SnapshotRow findById(Long snapshotId) {
        ModelExecutionSnapshotEntity snapshot = snapshotMapper.selectById(snapshotId);
        return snapshot == null ? null : toRow(snapshot);
    }

    private SnapshotRow toRow(ModelExecutionSnapshotEntity snapshot) {
        return new SnapshotRow(
            snapshot.getId(),
            snapshot.getAccountId(),
            snapshot.getProfileId(),
            snapshot.getProvider(),
            snapshot.getModel(),
            snapshot.getReasoningLevel(),
            snapshot.getEffectiveParametersJson(),
            snapshot.getCapabilityVersion(),
            snapshot.getModelCapabilityJson(),
            snapshot.getFallbackCapabilitiesJson(),
            snapshot.getCredentialId(),
            snapshot.getCustomEndpointUrl()
        );
    }
}
