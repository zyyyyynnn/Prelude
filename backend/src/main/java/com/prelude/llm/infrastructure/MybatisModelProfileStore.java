package com.prelude.llm.infrastructure;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.prelude.llm.application.port.ModelProfileStore;
import com.prelude.llm.application.port.ModelProfileStore.ProfileRow;
import com.prelude.llm.infrastructure.persistence.ModelProfileEntity;
import com.prelude.llm.infrastructure.persistence.ModelProfileMapper;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/** MyBatis-Plus adapter for {@link ModelProfileStore}; the only place that names the mapper. */
@Repository
@RequiredArgsConstructor
public class MybatisModelProfileStore implements ModelProfileStore {

    private final ModelProfileMapper profileMapper;

    @Override
    public Optional<ProfileRow> findActiveByAccount(Long accountId) {
        ModelProfileEntity profile = profileMapper.selectOne(new LambdaQueryWrapper<ModelProfileEntity>()
            .eq(ModelProfileEntity::getAccountId, accountId)
            .last("LIMIT 1"));
        return profile == null ? Optional.empty() : Optional.of(toRow(profile));
    }

    @Override
    public Optional<ProfileRow> findActiveForUpdate(Long accountId) {
        ModelProfileEntity profile = profileMapper.selectOne(new LambdaQueryWrapper<ModelProfileEntity>()
            .eq(ModelProfileEntity::getAccountId, accountId)
            .last("LIMIT 1 FOR UPDATE"));
        return profile == null ? Optional.empty() : Optional.of(toRow(profile));
    }

    @Override
    public ProfileRow insert(ProfileRow row) {
        ModelProfileEntity profile = new ModelProfileEntity();
        apply(profile, row);
        profileMapper.insert(profile);
        return toRow(profile);
    }

    @Override
    public void update(ProfileRow row) {
        ModelProfileEntity profile = new ModelProfileEntity();
        apply(profile, row);
        profile.setId(row.id());
        profileMapper.updateById(profile);
    }

    private void apply(ModelProfileEntity profile, ProfileRow row) {
        profile.setAccountId(row.accountId());
        profile.setProvider(row.provider());
        profile.setModel(row.model());
        profile.setCustomEndpointUrl(row.customEndpointUrl());
        profile.setReasoningLevel(row.reasoningLevel());
        profile.setEffectiveParametersJson(row.effectiveParametersJson());
        profile.setModelCapabilityJson(row.modelCapabilityJson());
        profile.setFallbackCapabilitiesJson(row.fallbackCapabilitiesJson());
        profile.setCredentialId(row.credentialId());
    }

    private ProfileRow toRow(ModelProfileEntity profile) {
        return new ProfileRow(
            profile.getId(),
            profile.getAccountId(),
            profile.getProvider(),
            profile.getModel(),
            profile.getCustomEndpointUrl(),
            profile.getReasoningLevel(),
            profile.getEffectiveParametersJson(),
            profile.getModelCapabilityJson(),
            profile.getFallbackCapabilitiesJson(),
            profile.getCredentialId()
        );
    }
}
