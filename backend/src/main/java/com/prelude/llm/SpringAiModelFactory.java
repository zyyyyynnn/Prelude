package com.prelude.llm;

import com.prelude.BusinessException;
import com.prelude.llm.api.LlmPort.ResponseMode;
import com.prelude.llm.api.ModelCapabilityResponse.ReasoningLevel;
import com.prelude.llm.persistence.ModelExecutionSnapshot;
import com.anthropic.backends.AnthropicBackend;
import com.anthropic.client.AnthropicClient;
import com.anthropic.client.AnthropicClientImpl;
import com.anthropic.models.messages.Model;
import com.anthropic.models.messages.OutputConfig;
import com.openai.client.OpenAIClient;
import com.openai.client.OpenAIClientImpl;
import com.openai.core.ClientOptions;
import com.openai.credential.BearerTokenCredential;
import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.setup.OpenAiSetup;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.time.Duration;
import tools.jackson.databind.ObjectMapper;

/**
 * Builds Spring AI ChatModel instances per immutable execution snapshot.
 * Provider/SDK retries are zero; ModelExecutionService is the sole retry
 * owner. Custom protocol execution uses Prelude's guarded DNS and redirect
 * policy; only the protocol gap missing from Spring AI receives a narrow adapter.
 */
@Component
public class SpringAiModelFactory {

    private static final Duration CALL_TIMEOUT = Duration.ofSeconds(60);
    private static final int ZERO_RETRIES = 0;
    private final String deepSeekApiKey;
    private final String deepSeekBaseUrl;
    private final CustomLlmEgressPolicy egressPolicy;
    private final EgressHttpClientFactory egressHttpClientFactory;
    private final ModelCapabilityCatalog capabilityCatalog;
    private final ObjectMapper objectMapper;

    public SpringAiModelFactory(
        @Value("${prelude.llm.provider.deepseek.api-key:}") String deepSeekApiKey,
        @Value("${prelude.llm.provider.deepseek.base-url:https://api.deepseek.com}") String deepSeekBaseUrl,
        CustomLlmEgressPolicy egressPolicy,
        EgressHttpClientFactory egressHttpClientFactory,
        ModelCapabilityCatalog capabilityCatalog,
        ObjectMapper objectMapper
    ) {
        this.deepSeekApiKey = deepSeekApiKey;
        this.deepSeekBaseUrl = deepSeekBaseUrl;
        this.egressPolicy = egressPolicy;
        this.egressHttpClientFactory = egressHttpClientFactory;
        this.capabilityCatalog = capabilityCatalog;
        this.objectMapper = objectMapper;
    }

    public ChatModel chatModel(ModelExecutionSnapshot snapshot, String apiKey) {
        capabilityCatalog.requireSupportedModel(snapshot.getProvider(), snapshot.getModel());
        return switch (snapshot.getProvider()) {
            case ModelCapabilityCatalog.PROVIDER_DEEPSEEK -> deepSeek(snapshot, apiKey);
            case "openai-responses" -> openAiResponses(snapshot, apiKey);
            case "openai-chat-completions" -> openAiChatCompletions(snapshot, apiKey);
            case "anthropic-messages" -> anthropicMessages(snapshot, apiKey);
            default -> throw BusinessException.badRequest("不支持的模型接入方式");
        };
    }

    public ChatOptions requestOptions(ModelExecutionSnapshot snapshot, ResponseMode responseMode) {
        return switch (snapshot.getProvider()) {
            case ModelCapabilityCatalog.PROVIDER_DEEPSEEK, "openai-chat-completions" ->
                openAiOptions(snapshot, responseMode);
            case "openai-responses" -> ChatOptions.builder()
                .model(snapshot.getModel())
                .maxTokens(executionParameters(snapshot).maxOutputTokens())
                .build();
            case "anthropic-messages" -> anthropicOptions(snapshot);
            default -> throw BusinessException.badRequest("不支持的模型接入方式");
        };
    }

    private ChatModel deepSeek(ModelExecutionSnapshot snapshot, String apiKey) {
        String key = apiKey != null && !apiKey.isBlank()
            ? apiKey
            : requireSystemKey(deepSeekApiKey, "DeepSeek");
        OpenAIClient client = OpenAiSetup.setupSyncClient(
            deepSeekBaseUrl,
            null,
            BearerTokenCredential.create(key),
            null,
            null,
            null,
            false,
            false,
            null,
            CALL_TIMEOUT,
            ZERO_RETRIES,
            null,
            java.util.Map.of(),
            io.micrometer.observation.ObservationRegistry.NOOP,
            io.micrometer.core.instrument.Metrics.globalRegistry,
            java.util.List.of()
        );
        return OpenAiChatModel.builder()
            .openAiClient(client)
            .openAiClientAsync(client.async())
            .options(openAiOptions(snapshot, ResponseMode.PLAIN_TEXT))
            .observationRegistry(io.micrometer.observation.ObservationRegistry.NOOP)
            .build();
    }

    private ChatModel openAiChatCompletions(ModelExecutionSnapshot snapshot, String apiKey) {
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

    private ChatModel openAiResponses(ModelExecutionSnapshot snapshot, String apiKey) {
        requireCustomApiKey(apiKey);
        URI root = egressPolicy.requireValidRoot(snapshot.getCustomEndpointUrl());
        return new OpenAiResponsesChatModel(
            trimTrailingSlash(root.toString()),
            apiKey,
            snapshot.getModel(),
            snapshot.getReasoningLevel(),
            executionParameters(snapshot).maxOutputTokens(),
            egressHttpClientFactory.runtimeClient(),
            objectMapper
        );
    }

    private ChatModel anthropicMessages(ModelExecutionSnapshot snapshot, String apiKey) {
        requireCustomApiKey(apiKey);
        URI root = egressPolicy.requireValidRoot(snapshot.getCustomEndpointUrl());
        String baseUrl = trimTrailingSlash(root.toString());
        AnthropicBackend backend = AnthropicBackend.builder()
            .baseUrl(baseUrl)
            .apiKey(apiKey)
            .build();
        com.anthropic.core.http.HttpClient transport = new com.anthropic.client.okhttp.OkHttpClient(
            egressHttpClientFactory.runtimeClient(), backend);
        com.anthropic.core.ClientOptions clientOptions = com.anthropic.core.ClientOptions.builder()
            .httpClient(transport)
            .baseUrl(baseUrl)
            .timeout(CALL_TIMEOUT)
            .maxRetries(ZERO_RETRIES)
            .build();
        AnthropicClient client = new AnthropicClientImpl(clientOptions);
        return AnthropicChatModel.builder()
            .anthropicClient(client)
            .anthropicClientAsync(client.async())
            .options(anthropicOptions(snapshot))
            .observationRegistry(io.micrometer.observation.ObservationRegistry.NOOP)
            .build();
    }

    private AnthropicChatOptions anthropicOptions(ModelExecutionSnapshot snapshot) {
        AnthropicChatOptions.Builder builder = AnthropicChatOptions.builder()
            .model(Model.of(snapshot.getModel()))
            .maxTokens(executionParameters(snapshot).maxOutputTokens());
        ReasoningLevel level = ReasoningLevel.valueOf(snapshot.getReasoningLevel());
        if (level != ReasoningLevel.AUTO) {
            builder.thinkingAdaptive()
                .effort(OutputConfig.Effort.of(level.name().toLowerCase(java.util.Locale.ROOT)));
        }
        return builder.build();
    }

    private OpenAiChatOptions openAiOptions(ModelExecutionSnapshot snapshot, ResponseMode responseMode) {
        OpenAiChatOptions.Builder builder = OpenAiChatOptions.builder()
            .model(snapshot.getModel())
            .maxTokens(executionParameters(snapshot).maxOutputTokens());
        ReasoningLevel level = ReasoningLevel.valueOf(snapshot.getReasoningLevel());
        if (level != ReasoningLevel.AUTO) {
            builder.reasoningEffort(level.name().toLowerCase(java.util.Locale.ROOT));
        }
        if (responseMode == ResponseMode.JSON_OBJECT
            && capabilityCatalog.capability(snapshot.getProvider(), snapshot.getModel()).structuredOutput()) {
            builder.responseFormat(OpenAiChatModel.ResponseFormat.builder()
                .type(OpenAiChatModel.ResponseFormat.Type.JSON_OBJECT)
                .build());
        }
        return builder.build();
    }

    private ModelExecutionParameters executionParameters(ModelExecutionSnapshot snapshot) {
        return ModelExecutionParameters.fromFrozenJson(snapshot.getEffectiveParametersJson(), objectMapper);
    }

    private void requireCustomApiKey(String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            throw BusinessException.badRequest("自定义端点 API Key 未配置");
        }
    }

    private String trimTrailingSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private String requireSystemKey(String systemKey, String providerName) {
        if (systemKey == null || systemKey.isBlank() || systemKey.startsWith("${")) {
            throw BusinessException.badRequest(providerName + " API Key 未配置");
        }
        return systemKey;
    }
}
