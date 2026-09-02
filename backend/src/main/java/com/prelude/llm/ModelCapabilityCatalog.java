package com.prelude.llm;

import com.prelude.BusinessException;
import com.prelude.llm.api.ModelCapabilityResponse;
import com.prelude.llm.api.ModelCapabilityResponse.ReasoningLevel;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Code-backed capability truth for the built-in models Prelude explicitly
 * supports. Models discovered from custom protocol endpoints remain
 * conservative except for capability facts established by the LLM module's
 * selected-model discovery path.
 */
@Component
public class ModelCapabilityCatalog {

    public static final String CAPABILITY_VERSION = "2026-09-02";

    public static final String PROVIDER_DEEPSEEK = "deepseek";

    private static final List<ReasoningLevel> AUTO_ONLY = List.of(ReasoningLevel.AUTO);
    private static final List<ReasoningLevel> DEEPSEEK_REASONING = List.of(
        ReasoningLevel.AUTO, ReasoningLevel.HIGH);

    private final Map<String, Map<String, ModelCapabilityResponse>> builtIns = builtIns();

    public ModelCapabilityResponse capability(String provider, String model) {
        if (CustomLlmProtocol.isCustom(provider)) {
            if (model == null || model.isBlank()) {
                return custom(provider, "");
            }
            return custom(provider, model.trim());
        }
        Map<String, ModelCapabilityResponse> providerModels = builtIns.get(provider);
        if (providerModels == null) {
            throw BusinessException.badRequest("不支持的模型接入方式");
        }
        ModelCapabilityResponse capability = providerModels.get(model);
        if (capability == null) {
            throw BusinessException.badRequest("当前接入方式不支持该模型");
        }
        return capability;
    }

    public void requireSupportedModel(String provider, String model) {
        if (model == null || model.isBlank()) {
            throw BusinessException.badRequest("模型不能为空");
        }
        capability(provider, model.trim());
    }

    public List<ModelCapabilityResponse> models(String provider) {
        if (CustomLlmProtocol.isCustom(provider)) {
            return List.of();
        }
        Map<String, ModelCapabilityResponse> models = builtIns.get(provider);
        if (models == null) {
            throw BusinessException.badRequest("不支持的模型接入方式");
        }
        return List.copyOf(models.values());
    }

    public List<String> knownProviders() {
        return java.util.stream.Stream.concat(
            java.util.stream.Stream.of(PROVIDER_DEEPSEEK),
            CustomLlmProtocol.providerKeys().stream()
        ).toList();
    }

    public String displayName(String provider) {
        if (PROVIDER_DEEPSEEK.equals(provider)) {
            return "DeepSeek";
        }
        return CustomLlmProtocol.require(provider).displayName();
    }

    private Map<String, Map<String, ModelCapabilityResponse>> builtIns() {
        Map<String, Map<String, ModelCapabilityResponse>> providers = new LinkedHashMap<>();

        Map<String, ModelCapabilityResponse> deepseek = new LinkedHashMap<>();
        deepseek.put("deepseek-v4-pro", capability(PROVIDER_DEEPSEEK, "deepseek-v4-pro",
            true, true, true, true, false, false, true, false, false, DEEPSEEK_REASONING));
        deepseek.put("deepseek-v4-flash", capability(PROVIDER_DEEPSEEK, "deepseek-v4-flash",
            true, true, true, true, false, false, true, false, false, DEEPSEEK_REASONING));
        providers.put(PROVIDER_DEEPSEEK, Map.copyOf(deepseek));

        return Map.copyOf(providers);
    }

    private ModelCapabilityResponse custom(String provider, String model) {
        return customCapability(provider, model, AUTO_ONLY);
    }

    ModelCapabilityResponse customCapability(
        String provider,
        String model,
        List<ReasoningLevel> supportedReasoningLevels
    ) {
        List<ReasoningLevel> levels = supportedReasoningLevels == null || supportedReasoningLevels.isEmpty()
            ? AUTO_ONLY
            : List.copyOf(supportedReasoningLevels);
        return capability(provider, model,
            levels.size() > 1, false, false, true, false, false, false, false, false, levels);
    }

    private ModelCapabilityResponse capability(
        String provider,
        String model,
        boolean reasoning,
        boolean structuredOutput,
        boolean toolCalling,
        boolean streaming,
        boolean vision,
        boolean multilingual,
        boolean longContext,
        boolean embedding,
        boolean nativeRealtimeVoice,
        List<ReasoningLevel> levels
    ) {
        return new ModelCapabilityResponse(
            provider, model, reasoning, structuredOutput, toolCalling, streaming, vision,
            multilingual, longContext, embedding, nativeRealtimeVoice, levels);
    }
}
