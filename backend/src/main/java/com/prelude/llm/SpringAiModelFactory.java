package com.prelude.llm;

import com.prelude.BusinessException;
import com.prelude.llm.api.LlmPort.ResponseMode;
import com.prelude.llm.api.ModelCapabilityResponse.ReasoningLevel;
import com.prelude.llm.persistence.ModelExecutionSnapshot;
import com.openai.client.OpenAIClient;
import com.openai.client.OpenAIClientImpl;
import com.openai.core.ClientOptions;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.deepseek.DeepSeekChatModel;
import org.springframework.ai.deepseek.DeepSeekChatOptions;
import org.springframework.ai.deepseek.api.DeepSeekApi;
import org.springframework.ai.deepseek.api.ResponseFormat;
import org.springframework.ai.model.tool.DefaultToolCallingManager;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.retry.RetryPolicy;
import org.springframework.core.retry.RetryTemplate;
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
            case ModelCapabilityCatalog.PROVIDER_DEEPSEEK -> deepSeekOptions(snapshot, responseMode);
            case "openai-chat-completions" -> openAiOptions(snapshot, responseMode);
            case "openai-responses", "anthropic-messages" -> ChatOptions.builder()
                .model(snapshot.getModel())
                .build();
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
        if (responseMode == ResponseMode.JSON_OBJECT
            && capabilityCatalog.capability(snapshot.getProvider(), snapshot.getModel()).structuredOutput()) {
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
            egressHttpClientFactory.runtimeClient(),
            objectMapper
        );
    }

    private ChatModel anthropicMessages(ModelExecutionSnapshot snapshot, String apiKey) {
        requireCustomApiKey(apiKey);
        URI root = egressPolicy.requireValidRoot(snapshot.getCustomEndpointUrl());
        return new AnthropicMessagesChatModel(
            trimTrailingSlash(root.toString()),
            apiKey,
            snapshot.getModel(),
            snapshot.getReasoningLevel(),
            egressHttpClientFactory.runtimeClient(),
            objectMapper
        );
    }

    private OpenAiChatOptions openAiOptions(ModelExecutionSnapshot snapshot, ResponseMode responseMode) {
        OpenAiChatOptions.Builder builder = OpenAiChatOptions.builder().model(snapshot.getModel());
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
