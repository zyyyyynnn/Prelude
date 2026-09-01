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
 * supports. Custom OpenAI-compatible models remain conservative until a
 * future probe owned by the LLM module can establish more facts.
 */
@Component
public class ModelCapabilityCatalog {

    public static final String CAPABILITY_VERSION = "2026-09-01";

    public static final String PROVIDER_DEEPSEEK = "deepseek";
    public static final String PROVIDER_OPENAI = "openai";
    public static final String PROVIDER_ANTHROPIC = "anthropic";
    public static final String PROVIDER_OPENAI_COMPATIBLE = "openai-compatible";

    private static final List<ReasoningLevel> AUTO_ONLY = List.of(ReasoningLevel.AUTO);
    private static final List<ReasoningLevel> ALL_REASONING = List.of(
        ReasoningLevel.AUTO, ReasoningLevel.LOW, ReasoningLevel.MEDIUM, ReasoningLevel.HIGH);
    private static final List<ReasoningLevel> DEEPSEEK_REASONING = List.of(
        ReasoningLevel.AUTO, ReasoningLevel.HIGH);

    private final Map<String, Map<String, ModelCapabilityResponse>> builtIns = builtIns();

    public ModelCapabilityResponse capability(String provider, String model) {
        if (PROVIDER_OPENAI_COMPATIBLE.equals(provider)) {
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

    public List<String> models(String provider) {
        if (PROVIDER_OPENAI_COMPATIBLE.equals(provider)) {
            return List.of();
        }
        Map<String, ModelCapabilityResponse> models = builtIns.get(provider);
        if (models == null) {
            throw BusinessException.badRequest("不支持的模型接入方式");
        }
        return List.copyOf(models.keySet());
    }

    public List<String> knownProviders() {
        return List.of(PROVIDER_DEEPSEEK, PROVIDER_OPENAI, PROVIDER_ANTHROPIC, PROVIDER_OPENAI_COMPATIBLE);
    }

    public String displayName(String provider) {
        return Map.of(
            PROVIDER_DEEPSEEK, "DeepSeek",
            PROVIDER_OPENAI, "OpenAI",
            PROVIDER_ANTHROPIC, "Anthropic",
            PROVIDER_OPENAI_COMPATIBLE, "OpenAI 兼容端点"
        ).get(provider);
    }

    private Map<String, Map<String, ModelCapabilityResponse>> builtIns() {
        Map<String, Map<String, ModelCapabilityResponse>> providers = new LinkedHashMap<>();

        Map<String, ModelCapabilityResponse> deepseek = new LinkedHashMap<>();
        deepseek.put("deepseek-v4-pro", capability(PROVIDER_DEEPSEEK, "deepseek-v4-pro",
            true, true, true, true, true, true, true, false, false, DEEPSEEK_REASONING));
        deepseek.put("deepseek-v4-flash", capability(PROVIDER_DEEPSEEK, "deepseek-v4-flash",
            true, true, true, true, true, true, true, false, false, DEEPSEEK_REASONING));
        deepseek.put("deepseek-chat", capability(PROVIDER_DEEPSEEK, "deepseek-chat",
            false, true, true, true, false, true, true, false, false, AUTO_ONLY));
        deepseek.put("deepseek-reasoner", capability(PROVIDER_DEEPSEEK, "deepseek-reasoner",
            true, true, false, true, false, true, true, false, false, DEEPSEEK_REASONING));
        providers.put(PROVIDER_DEEPSEEK, Map.copyOf(deepseek));

        providers.put(PROVIDER_OPENAI, Map.of(
            "gpt-5.4", capability(PROVIDER_OPENAI, "gpt-5.4",
                true, true, true, true, true, true, true, false, false, ALL_REASONING)
        ));

        providers.put(PROVIDER_ANTHROPIC, Map.of(
            "claude-sonnet-4-6", capability(PROVIDER_ANTHROPIC, "claude-sonnet-4-6",
                true, true, true, true, true, true, true, false, false, ALL_REASONING)
        ));
        return Map.copyOf(providers);
    }

    private ModelCapabilityResponse custom(String provider, String model) {
        return capability(provider, model,
            false, false, false, true, false, false, false, false, false, AUTO_ONLY);
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
