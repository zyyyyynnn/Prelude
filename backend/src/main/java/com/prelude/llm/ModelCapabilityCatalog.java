package com.prelude.llm;

import com.prelude.BusinessException;
import com.prelude.llm.api.ModelCapabilityResponse;
import com.prelude.llm.api.ModelCapabilityResponse.ReasoningLevel;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Provider capability catalog. Capabilities and reasoning levels come from
 * known Spring AI adapter metadata — the UI never guesses. Unsupported
 * reasoning levels are not exposed and never silently downgraded.
 */
@Component
public class ModelCapabilityCatalog {

    public static final String CAPABILITY_VERSION = "2026-08-31";

    public static final String PROVIDER_DEEPSEEK = "deepseek";
    public static final String PROVIDER_OPENAI = "openai";
    public static final String PROVIDER_ANTHROPIC = "anthropic";
    public static final String PROVIDER_OPENAI_COMPATIBLE = "openai-compatible";

    /**
     * Models without an explicit catalog entry still resolve provider-level
     * reasoning defaults; unknown models expose AUTO only.
     */
    public ModelCapabilityResponse capability(String provider, String model) {
        return switch (provider) {
            case PROVIDER_DEEPSEEK -> new ModelCapabilityResponse(
                provider, model, true, true, true, true, false, true, false, false, false,
                List.of(ReasoningLevel.AUTO, ReasoningLevel.HIGH));
            case PROVIDER_OPENAI -> new ModelCapabilityResponse(
                provider, model, true, true, true, true, true, true, false, true, false,
                List.of(ReasoningLevel.AUTO, ReasoningLevel.LOW, ReasoningLevel.MEDIUM, ReasoningLevel.HIGH));
            case PROVIDER_ANTHROPIC -> new ModelCapabilityResponse(
                provider, model, true, true, true, true, true, true, true, false, false,
                List.of(ReasoningLevel.AUTO, ReasoningLevel.LOW, ReasoningLevel.MEDIUM, ReasoningLevel.HIGH));
            case PROVIDER_OPENAI_COMPATIBLE -> new ModelCapabilityResponse(
                provider, model, false, false, false, true, false, false, false, false, false,
                List.of(ReasoningLevel.AUTO));
            default -> throw BusinessException.badRequest("不支持的模型接入方式");
        };
    }

    public boolean supportsReasoning(String provider, ReasoningLevel level) {
        return capability(provider, "").supportedReasoningLevels().contains(level);
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
}
