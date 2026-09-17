package com.prelude.llm;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.prelude.BusinessException;
import com.prelude.llm.api.ModelCapabilityResponse;
import com.prelude.llm.persistence.ModelProfile;
import com.prelude.llm.persistence.ModelProfileMapper;

/**
 * Shared profile loading and frozen-capability resolution. Kept free of the
 * full profile configuration service so freeze/execution stay on a short path.
 */
final class ProfileCapabilities {

    private ProfileCapabilities() {
    }

    static ModelProfile requireProfile(ModelProfileMapper profileMapper, Long accountId) {
        ModelProfile profile = profileMapper.selectOne(new LambdaQueryWrapper<ModelProfile>()
            .eq(ModelProfile::getAccountId, accountId)
            .last("LIMIT 1"));
        if (profile == null) {
            throw BusinessException.badRequest("请先配置模型服务");
        }
        return profile;
    }

    static ModelCapabilityResponse capabilityForProfile(
        ModelProfile profile,
        String model,
        ModelCapabilityCatalog capabilityCatalog,
        ModelCapabilityJson capabilityJson
    ) {
        if (!CustomLlmProtocol.isCustom(profile.getProvider())) {
            return capabilityCatalog.capability(profile.getProvider(), model);
        }
        if (profile.getModel().equals(model)) {
            ModelCapabilityResponse stored = capabilityJson.read(profile.getModelCapabilityJson());
            if (profile.getProvider().equals(stored.provider()) && model.equals(stored.model())) {
                return stored;
            }
        }
        return capabilityJson.readList(profile.getFallbackCapabilitiesJson()).stream()
            .filter(capability -> profile.getProvider().equals(capability.provider())
                && model.equals(capability.model()))
            .findFirst()
            .orElseThrow(() -> BusinessException.badRequest("所选模型能力尚未确认，请先保存模型配置"));
    }
}
