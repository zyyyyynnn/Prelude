package com.prelude.llm;

import com.prelude.BusinessException;
import com.prelude.llm.api.ModelCapabilityResponse.ReasoningLevel;
import com.prelude.llm.persistence.ModelExecutionSnapshot;
import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.anthropic.AnthropicSetup;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.deepseek.DeepSeekChatModel;
import org.springframework.ai.deepseek.DeepSeekChatOptions;
import org.springframework.ai.deepseek.api.DeepSeekApi;
import org.springframework.ai.model.tool.DefaultToolCallingManager;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.setup.OpenAiSetup;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Builds Spring AI ChatModel instances per frozen execution snapshot. BYOK is
 * per-account/per-profile, so models are constructed programmatically from the
 * snapshot's credential and endpoint; Spring AI's own HTTP retry is pinned to
 * one actual attempt so Prelude's bounded transport retry stays the single
 * retry owner.
 */
@Component
public class SpringAiModelFactory {

    private static final Duration CALL_TIMEOUT = Duration.ofSeconds(60);
    private static final int ONE_ATTEMPT = 1;

    private final String deepSeekApiKey;
    private final String deepSeekBaseUrl;
    private final String openAiApiKey;
    private final String anthropicApiKey;
    private final CustomLlmEgressPolicy egressPolicy;
    private final EgressHttpClientFactory egressHttpClientFactory;

    public SpringAiModelFactory(
        @Value("${prelude.llm.provider.deepseek.api-key:}") String deepSeekApiKey,
        @Value("${prelude.llm.provider.deepseek.base-url:https://api.deepseek.com}") String deepSeekBaseUrl,
        @Value("${prelude.llm.provider.openai.api-key:}") String openAiApiKey,
        @Value("${prelude.llm.provider.anthropic.api-key:}") String anthropicApiKey,
        CustomLlmEgressPolicy egressPolicy,
        EgressHttpClientFactory egressHttpClientFactory
    ) {
        this.deepSeekApiKey = deepSeekApiKey;
        this.deepSeekBaseUrl = deepSeekBaseUrl;
        this.openAiApiKey = openAiApiKey;
        this.anthropicApiKey = anthropicApiKey;
        this.egressPolicy = egressPolicy;
        this.egressHttpClientFactory = egressHttpClientFactory;
    }

    /**
     * A RetryPolicy that never retries: Prelude's bounded Resilience4j retry in
     * ModelExecutionService is the single transport retry owner.
     */
    private static org.springframework.core.retry.RetryPolicy noRetryPolicy() {
        return org.springframework.core.retry.RetryPolicy.withDefaults();
    }

    public ChatModel chatModel(ModelExecutionSnapshot snapshot, String apiKey) {
        return switch (snapshot.getProvider()) {
            case ModelCapabilityCatalog.PROVIDER_DEEPSEEK -> deepSeek(snapshot, apiKey);
            case ModelCapabilityCatalog.PROVIDER_OPENAI -> openAi(snapshot, apiKey, null);
            case ModelCapabilityCatalog.PROVIDER_ANTHROPIC -> anthropic(snapshot, apiKey);
            case ModelCapabilityCatalog.PROVIDER_OPENAI_COMPATIBLE -> openAiCompatible(snapshot, apiKey);
            default -> throw BusinessException.badRequest("不支持的模型接入方式");
        };
    }

    private ChatModel deepSeek(ModelExecutionSnapshot snapshot, String apiKey) {
        String key = apiKey != null && !apiKey.isBlank() ? apiKey : requireSystemKey(deepSeekApiKey, "DeepSeek");
        DeepSeekApi api = DeepSeekApi.builder()
            .baseUrl(deepSeekBaseUrl)
            .apiKey(key)
            .build();
        return new DeepSeekChatModel(
            api,
            deepSeekOptions(snapshot),
            DefaultToolCallingManager.builder().build(),
            new RetryTemplate(noRetryPolicy()),
            io.micrometer.observation.ObservationRegistry.NOOP
        );
    }

    /**
     * DeepSeek's Spring AI options are enum-typed on known models; the model
     * string maps onto the enum, and HIGH selects the dedicated reasoner
     * configuration. Unknown model strings keep the enum default and are
     * validated by the capability catalog upstream.
     */
    private DeepSeekChatOptions deepSeekOptions(ModelExecutionSnapshot snapshot) {
        var builder = DeepSeekChatOptions.builder()
            .model(toDeepSeekModel(snapshot.getModel()));
        ReasoningLevel level = ReasoningLevel.valueOf(snapshot.getReasoningLevel());
        if (level == ReasoningLevel.HIGH) {
            builder.reasoningEffortHigh();
        }
        return builder.build();
    }

    private DeepSeekApi.ChatModel toDeepSeekModel(String model) {
        for (DeepSeekApi.ChatModel known : DeepSeekApi.ChatModel.values()) {
            if (known.getValue().equals(model) || known.getName().equalsIgnoreCase(model)) {
                return known;
            }
        }
        return DeepSeekApi.ChatModel.DEEPSEEK_CHAT;
    }

    private ChatModel openAi(ModelExecutionSnapshot snapshot, String apiKey, URI customRoot) {
        String key = apiKey != null && !apiKey.isBlank()
            ? apiKey
            : requireSystemKey(openAiApiKey, "OpenAI");
        var client = OpenAiSetup.setupSyncClient(
            customRoot == null ? null : customRoot.toString(),
            null,
            com.openai.credential.BearerTokenCredential.create(key),
            null, null, null, false, false, null,
            CALL_TIMEOUT, ONE_ATTEMPT, null, Map.of(),
            io.micrometer.observation.ObservationRegistry.NOOP,
            io.micrometer.core.instrument.Metrics.globalRegistry,
            List.of()
        );
        return OpenAiChatModel.builder()
            .openAiClient(client)
            .options(openAiOptions(snapshot))
            .observationRegistry(io.micrometer.observation.ObservationRegistry.NOOP)
            .build();
    }

    /**
     * OpenAI-compatible custom endpoint: Spring AI's OpenAI protocol
     * primitives pointed at the snapshot's runtime base URL and credential,
     * with the egress policy applied to the transport.
     */
    private ChatModel openAiCompatible(ModelExecutionSnapshot snapshot, String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            throw BusinessException.badRequest("自定义端点 API Key 未配置");
        }
        URI root = egressPolicy.requireValidRoot(snapshot.getCustomEndpointUrl());
        var client = OpenAiSetup.setupSyncClient(
            root.toString(),
            null,
            com.openai.credential.BearerTokenCredential.create(apiKey),
            null, null, null, false, false, null,
            CALL_TIMEOUT, ONE_ATTEMPT, null, Map.of(),
            io.micrometer.observation.ObservationRegistry.NOOP,
            io.micrometer.core.instrument.Metrics.globalRegistry,
            List.of(builder -> egressHttpClientFactory.openAiInterceptors().forEach(builder::interceptor))
        );
        return OpenAiChatModel.builder()
            .openAiClient(client)
            .options(openAiOptions(snapshot))
            .observationRegistry(io.micrometer.observation.ObservationRegistry.NOOP)
            .build();
    }

    private OpenAiChatOptions openAiOptions(ModelExecutionSnapshot snapshot) {
        var builder = OpenAiChatOptions.builder()
            .model(snapshot.getModel());
        ReasoningLevel level = ReasoningLevel.valueOf(snapshot.getReasoningLevel());
        if (level != ReasoningLevel.AUTO) {
            builder.reasoningEffort(level.name().toLowerCase(java.util.Locale.ROOT));
        }
        return builder.build();
    }

    private ChatModel anthropic(ModelExecutionSnapshot snapshot, String apiKey) {
        String key = apiKey != null && !apiKey.isBlank()
            ? apiKey
            : requireSystemKey(anthropicApiKey, "Anthropic");
        var client = AnthropicSetup.setupSyncClient(
            null, key, CALL_TIMEOUT, ONE_ATTEMPT, null, Map.of(),
            io.micrometer.observation.ObservationRegistry.NOOP,
            io.micrometer.core.instrument.Metrics.globalRegistry
        );
        return AnthropicChatModel.builder()
            .anthropicClient(client)
            .options(anthropicOptions(snapshot))
            .observationRegistry(io.micrometer.observation.ObservationRegistry.NOOP)
            .build();
    }

    private AnthropicChatOptions anthropicOptions(ModelExecutionSnapshot snapshot) {
        var builder = AnthropicChatOptions.builder();
        ReasoningLevel level = ReasoningLevel.valueOf(snapshot.getReasoningLevel());
        switch (level) {
            case LOW, MEDIUM -> builder.thinkingEnabled(2048L);
            case HIGH -> builder.thinkingEnabled(8192L);
            case AUTO -> {
                // provider default behavior; no explicit thinking parameter
            }
        }
        return builder.build();
    }

    private String requireSystemKey(String systemKey, String providerName) {
        if (systemKey == null || systemKey.isBlank() || systemKey.startsWith("${")) {
            throw BusinessException.badRequest(providerName + " API Key 未配置");
        }
        return systemKey;
    }

    /**
     * Structured-output request option used by report/judge generations that
     * expect strict JSON. Providers without native JSON mode ignore the
     * response-format body and rely on prompt-level schema instructions.
     */
    public Optional<ChatOptions> responseFormatJson(String provider) {
        if (ModelCapabilityCatalog.PROVIDER_DEEPSEEK.equals(provider)
            || ModelCapabilityCatalog.PROVIDER_OPENAI.equals(provider)
            || ModelCapabilityCatalog.PROVIDER_OPENAI_COMPATIBLE.equals(provider)) {
            return Optional.of(OpenAiChatOptions.builder()
                .extraBody(Map.of("response_format", Map.of("type", "json_object")))
                .build());
        }
        return Optional.empty();
    }
}
