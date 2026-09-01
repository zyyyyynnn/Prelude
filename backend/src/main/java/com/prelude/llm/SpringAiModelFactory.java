package com.prelude.llm;

import com.prelude.BusinessException;
import com.prelude.llm.api.LlmPort.ResponseMode;
import com.prelude.llm.api.ModelCapabilityResponse.ReasoningLevel;
import com.prelude.llm.persistence.ModelExecutionSnapshot;
import com.openai.client.OpenAIClient;
import com.openai.client.OpenAIClientImpl;
import com.openai.core.ClientOptions;
import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.anthropic.AnthropicSetup;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.deepseek.DeepSeekChatModel;
import org.springframework.ai.deepseek.DeepSeekChatOptions;
import org.springframework.ai.deepseek.api.DeepSeekApi;
import org.springframework.ai.deepseek.api.ResponseFormat;
import org.springframework.ai.model.tool.DefaultToolCallingManager;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.setup.OpenAiSetup;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.retry.RetryPolicy;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Builds Spring AI ChatModel instances per immutable execution snapshot.
 * Provider/SDK retries are zero; ModelExecutionService is the sole retry
 * owner. Custom OpenAI-compatible execution uses the official OpenAI OkHttp
 * client with Prelude's guarded DNS and redirect policy.
 */
@Component
public class SpringAiModelFactory {

    private static final Duration CALL_TIMEOUT = Duration.ofSeconds(60);
    private static final int ZERO_RETRIES = 0;
    private static final String GENERIC_OBJECT_SCHEMA = "{\"type\":\"object\"}";

    private final String deepSeekApiKey;
    private final String deepSeekBaseUrl;
    private final String openAiApiKey;
    private final String anthropicApiKey;
    private final CustomLlmEgressPolicy egressPolicy;
    private final EgressHttpClientFactory egressHttpClientFactory;
    private final ModelCapabilityCatalog capabilityCatalog;

    public SpringAiModelFactory(
        @Value("${prelude.llm.provider.deepseek.api-key:}") String deepSeekApiKey,
        @Value("${prelude.llm.provider.deepseek.base-url:https://api.deepseek.com}") String deepSeekBaseUrl,
        @Value("${prelude.llm.provider.openai.api-key:}") String openAiApiKey,
        @Value("${prelude.llm.provider.anthropic.api-key:}") String anthropicApiKey,
        CustomLlmEgressPolicy egressPolicy,
        EgressHttpClientFactory egressHttpClientFactory,
        ModelCapabilityCatalog capabilityCatalog
    ) {
        this.deepSeekApiKey = deepSeekApiKey;
        this.deepSeekBaseUrl = deepSeekBaseUrl;
        this.openAiApiKey = openAiApiKey;
        this.anthropicApiKey = anthropicApiKey;
        this.egressPolicy = egressPolicy;
        this.egressHttpClientFactory = egressHttpClientFactory;
        this.capabilityCatalog = capabilityCatalog;
    }

    public ChatModel chatModel(ModelExecutionSnapshot snapshot, String apiKey) {
        capabilityCatalog.requireSupportedModel(snapshot.getProvider(), snapshot.getModel());
        return switch (snapshot.getProvider()) {
            case ModelCapabilityCatalog.PROVIDER_DEEPSEEK -> deepSeek(snapshot, apiKey);
            case ModelCapabilityCatalog.PROVIDER_OPENAI -> openAi(snapshot, apiKey);
            case ModelCapabilityCatalog.PROVIDER_ANTHROPIC -> anthropic(snapshot, apiKey);
            case ModelCapabilityCatalog.PROVIDER_OPENAI_COMPATIBLE -> openAiCompatible(snapshot, apiKey);
            default -> throw BusinessException.badRequest("不支持的模型接入方式");
        };
    }

    public ChatOptions requestOptions(ModelExecutionSnapshot snapshot, ResponseMode responseMode) {
        if (responseMode == ResponseMode.JSON
            && !capabilityCatalog.capability(snapshot.getProvider(), snapshot.getModel()).structuredOutput()) {
            throw BusinessException.badRequest("所选模型不支持结构化输出");
        }
        return switch (snapshot.getProvider()) {
            case ModelCapabilityCatalog.PROVIDER_DEEPSEEK -> deepSeekOptions(snapshot, responseMode);
            case ModelCapabilityCatalog.PROVIDER_OPENAI,
                 ModelCapabilityCatalog.PROVIDER_OPENAI_COMPATIBLE -> openAiOptions(snapshot, responseMode);
            case ModelCapabilityCatalog.PROVIDER_ANTHROPIC -> anthropicOptions(snapshot, responseMode);
            default -> throw BusinessException.badRequest("不支持的模型接入方式");
        };
    }

    private ChatModel deepSeek(ModelExecutionSnapshot snapshot, String apiKey) {
        String key = apiKey != null && !apiKey.isBlank()
            ? apiKey
            : requireSystemKey(deepSeekApiKey, "DeepSeek");
        DeepSeekApi api = DeepSeekApi.builder().baseUrl(deepSeekBaseUrl).apiKey(key).build();
        return new DeepSeekChatModel(
            api,
            deepSeekOptions(snapshot, ResponseMode.PLAIN_TEXT),
            DefaultToolCallingManager.builder().build(),
            new RetryTemplate(RetryPolicy.builder().maxRetries(0).build()),
            io.micrometer.observation.ObservationRegistry.NOOP
        );
    }

    private DeepSeekChatOptions deepSeekOptions(ModelExecutionSnapshot snapshot, ResponseMode responseMode) {
        DeepSeekChatOptions.Builder builder = DeepSeekChatOptions.builder()
            .model(requireDeepSeekModel(snapshot.getModel()));
        ReasoningLevel level = ReasoningLevel.valueOf(snapshot.getReasoningLevel());
        if (level == ReasoningLevel.HIGH) {
            builder.reasoningEffortHigh();
        }
        if (responseMode == ResponseMode.JSON) {
            builder.responseFormat(ResponseFormat.builder().type(ResponseFormat.Type.JSON_OBJECT).build());
        }
        return builder.build();
    }

    private DeepSeekApi.ChatModel requireDeepSeekModel(String model) {
        for (DeepSeekApi.ChatModel known : DeepSeekApi.ChatModel.values()) {
            if (known.getValue().equals(model) || known.getName().equalsIgnoreCase(model)) {
                return known;
            }
        }
        throw BusinessException.badRequest("DeepSeek 不支持该模型");
    }

    private ChatModel openAi(ModelExecutionSnapshot snapshot, String apiKey) {
        String key = apiKey != null && !apiKey.isBlank()
            ? apiKey
            : requireSystemKey(openAiApiKey, "OpenAI");
        OpenAIClient client = OpenAiSetup.setupSyncClient(
            null, null, com.openai.credential.BearerTokenCredential.create(key),
            null, null, null, false, false, null,
            CALL_TIMEOUT, ZERO_RETRIES, null, Map.of(),
            io.micrometer.observation.ObservationRegistry.NOOP,
            io.micrometer.core.instrument.Metrics.globalRegistry,
            List.of());
        return OpenAiChatModel.builder()
            .openAiClient(client)
            .openAiClientAsync(client.async())
            .options(openAiOptions(snapshot, ResponseMode.PLAIN_TEXT))
            .observationRegistry(io.micrometer.observation.ObservationRegistry.NOOP)
            .build();
    }

    private ChatModel openAiCompatible(ModelExecutionSnapshot snapshot, String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            throw BusinessException.badRequest("自定义端点 API Key 未配置");
        }
        URI root = egressPolicy.requireValidRoot(snapshot.getCustomEndpointUrl());
        okhttp3.OkHttpClient guardedClient = egressHttpClientFactory.runtimeClient();
        com.openai.core.http.HttpClient transport = new com.openai.client.okhttp.OkHttpClient(guardedClient);
        ClientOptions options = ClientOptions.builder()
            .httpClient(transport)
            .baseUrl(root.toString())
            .credential(com.openai.credential.BearerTokenCredential.create(apiKey))
            .timeout(CALL_TIMEOUT)
            .maxRetries(ZERO_RETRIES)
            .build();
        OpenAIClient client = new OpenAIClientImpl(options);
        return OpenAiChatModel.builder()
            .openAiClient(client)
            .openAiClientAsync(client.async())
            .options(openAiOptions(snapshot, ResponseMode.PLAIN_TEXT))
            .observationRegistry(io.micrometer.observation.ObservationRegistry.NOOP)
            .build();
    }

    private OpenAiChatOptions openAiOptions(ModelExecutionSnapshot snapshot, ResponseMode responseMode) {
        OpenAiChatOptions.Builder builder = OpenAiChatOptions.builder().model(snapshot.getModel());
        ReasoningLevel level = ReasoningLevel.valueOf(snapshot.getReasoningLevel());
        if (level != ReasoningLevel.AUTO) {
            builder.reasoningEffort(level.name().toLowerCase(java.util.Locale.ROOT));
        }
        if (responseMode == ResponseMode.JSON) {
            builder.responseFormat(OpenAiChatModel.ResponseFormat.builder()
                .type(OpenAiChatModel.ResponseFormat.Type.JSON_OBJECT)
                .build());
        }
        return builder.build();
    }

    private ChatModel anthropic(ModelExecutionSnapshot snapshot, String apiKey) {
        String key = apiKey != null && !apiKey.isBlank()
            ? apiKey
            : requireSystemKey(anthropicApiKey, "Anthropic");
        var client = AnthropicSetup.setupSyncClient(
            null, key, CALL_TIMEOUT, ZERO_RETRIES, null, Map.of(),
            io.micrometer.observation.ObservationRegistry.NOOP,
            io.micrometer.core.instrument.Metrics.globalRegistry);
        return AnthropicChatModel.builder()
            .anthropicClient(client)
            .options(anthropicOptions(snapshot, ResponseMode.PLAIN_TEXT))
            .observationRegistry(io.micrometer.observation.ObservationRegistry.NOOP)
            .build();
    }

    private AnthropicChatOptions anthropicOptions(ModelExecutionSnapshot snapshot, ResponseMode responseMode) {
        AnthropicChatOptions.Builder builder = AnthropicChatOptions.builder()
            .model(com.anthropic.models.messages.Model.of(snapshot.getModel()))
            .maxRetries(ZERO_RETRIES);
        ReasoningLevel level = ReasoningLevel.valueOf(snapshot.getReasoningLevel());
        switch (level) {
            case LOW, MEDIUM -> builder.thinkingEnabled(2048L);
            case HIGH -> builder.thinkingEnabled(8192L);
            case AUTO -> {
                // Provider default.
            }
        }
        if (responseMode == ResponseMode.JSON) {
            builder.outputSchema(GENERIC_OBJECT_SCHEMA);
        }
        return builder.build();
    }

    private String requireSystemKey(String systemKey, String providerName) {
        if (systemKey == null || systemKey.isBlank() || systemKey.startsWith("${")) {
            throw BusinessException.badRequest(providerName + " API Key 未配置");
        }
        return systemKey;
    }
}
