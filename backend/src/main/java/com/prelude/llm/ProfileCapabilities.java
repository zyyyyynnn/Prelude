package com.prelude.llm;

import com.prelude.BusinessException;
import com.prelude.llm.api.ModelCapabilityResponse;
import com.prelude.llm.application.port.ModelProfileStore;
import com.prelude.llm.application.port.ModelProfileStore.ProfileRow;

/**
 * Shared profile loading and frozen-capability resolution. Kept free of the
 * full profile configuration service so freeze/execution stay on a short path.
 */
final class ProfileCapabilities {

    private ProfileCapabilities() {
    }

    static ProfileRow requireProfile(ModelProfileStore profileStore, Long accountId) {
        return profileStore.findActiveByAccount(accountId)
            .orElseThrow(() -> BusinessException.badRequest("请先配置模型服务"));
    }

    static ModelCapabilityResponse capabilityForProfile(
        ProfileRow profile,
        String model,
        ModelCapabilityCatalog capabilityCatalog,
        ModelCapabilityJson capabilityJson
    ) {
        if (!CustomLlmProtocol.isCustom(profile.provider())) {
            return capabilityCatalog.capability(profile.provider(), model);
        }
        if (profile.model().equals(model)) {
            ModelCapabilityResponse stored = capabilityJson.read(profile.modelCapabilityJson());
            if (profile.provider().equals(stored.provider()) && model.equals(stored.model())) {
                return stored;
            }
        }
        return capabilityJson.readList(profile.fallbackCapabilitiesJson()).stream()
            .filter(capability -> profile.provider().equals(capability.provider())
                && model.equals(capability.model()))
            .findFirst()
            .orElseThrow(() -> BusinessException.badRequest("所选模型能力尚未确认，请先保存模型配置"));
    }
}
