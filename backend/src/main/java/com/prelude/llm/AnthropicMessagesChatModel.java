package com.prelude.llm;

import com.prelude.llm.api.ModelCapabilityResponse;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.retry.NonTransientAiException;
import org.springframework.ai.retry.TransientAiException;
import reactor.core.publisher.Flux;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Narrow Spring AI ChatModel adapter for user-supplied Anthropic Messages endpoints. */
final class AnthropicMessagesChatModel implements ChatModel {

    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private static final String ANTHROPIC_VERSION = "2023-06-01";
    private static final int DEFAULT_MAX_TOKENS = 4096;

    private final String endpoint;
    private final String apiKey;
    private final String model;
    private final ModelCapabilityResponse.ReasoningLevel reasoningLevel;
    private final okhttp3.OkHttpClient httpClient;
    private final ObjectMapper objectMapper;

    AnthropicMessagesChatModel(
        String root,
        String apiKey,
        String model,
        String reasoningLevel,
        okhttp3.OkHttpClient httpClient,
        ObjectMapper objectMapper
    ) {
        this.endpoint = root + CustomLlmProtocol.ANTHROPIC_MESSAGES.endpointSuffix();
        this.apiKey = apiKey;
        this.model = model;
        this.reasoningLevel = ModelCapabilityResponse.ReasoningLevel.valueOf(reasoningLevel);
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public ChatResponse call(Prompt prompt) {
        try (Response response = httpClient.newCall(request(prompt, false)).execute()) {
            requireSuccess(response);
            String body = response.body() == null ? "" : response.body().string();
            JsonNode root = objectMapper.readTree(body);
            String text = root.path("content").path(0).path("text").asString("");
            if (text.isBlank()) {
                throw new NonTransientAiException("Anthropic Messages returned no text");
            }
            return response(text, root.path("usage"));
        } catch (IOException exception) {
            throw new TransientAiException("Anthropic Messages transport failure", exception);
        }
    }

    @Override
    public Flux<ChatResponse> stream(Prompt prompt) {
        return Flux.create(sink -> {
            int[] tokens = new int[] {0, 0};
            try (Response response = httpClient.newCall(request(prompt, true)).execute()) {
                requireSuccess(response);
                if (response.body() == null) {
                    throw new TransientAiException("Anthropic Messages stream returned no body");
                }
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                    response.body().byteStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null && !sink.isCancelled()) {
                        String trimmed = line.trim();
                        if (!trimmed.startsWith("data:")) {
                            continue;
                        }
                        JsonNode event = objectMapper.readTree(trimmed.substring("data:".length()).trim());
                        String type = event.path("type").asString("");
                        if ("message_start".equals(type)) {
                            tokens[0] = event.path("message").path("usage").path("input_tokens").asInt(0);
                        } else if ("content_block_delta".equals(type)) {
                            String delta = event.path("delta").path("text").asString("");
                            if (!delta.isEmpty()) {
                                sink.next(response(delta, null));
                            }
                        } else if ("message_delta".equals(type)) {
                            tokens[1] = event.path("usage").path("output_tokens").asInt(tokens[1]);
                        } else if ("message_stop".equals(type)) {
                            sink.next(response("", tokens[0], tokens[1]));
                        } else if ("error".equals(type)) {
                            throw new NonTransientAiException("Anthropic Messages stream failed");
                        }
                    }
                }
                sink.complete();
            } catch (IOException exception) {
                sink.error(new TransientAiException("Anthropic Messages stream transport failure", exception));
            } catch (RuntimeException exception) {
                sink.error(exception);
            }
        });
    }

    private Request request(Prompt prompt, boolean stream) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", model);
        payload.put("max_tokens", DEFAULT_MAX_TOKENS);
        payload.put("stream", stream);
        if (reasoningLevel != ModelCapabilityResponse.ReasoningLevel.AUTO) {
            payload.put("thinking", Map.of("type", "adaptive"));
            payload.put("output_config", Map.of(
                "effort", reasoningLevel.name().toLowerCase(java.util.Locale.ROOT)));
        }
        String system = prompt.getInstructions().stream()
            .filter(message -> message.getMessageType() == MessageType.SYSTEM)
            .map(Message::getText)
            .filter(text -> !text.isBlank())
            .reduce((left, right) -> left + "\n\n" + right)
            .orElse("");
        if (!system.isBlank()) {
            payload.put("system", system);
        }
        payload.put("messages", prompt.getInstructions().stream()
            .filter(message -> message.getMessageType() != MessageType.SYSTEM)
            .map(message -> Map.of(
                "role", message.getMessageType() == MessageType.ASSISTANT ? "assistant" : "user",
                "content", message.getText()))
            .toList());
        try {
            return new Request.Builder()
                .url(endpoint)
                .addHeader("x-api-key", apiKey)
                .addHeader("anthropic-version", ANTHROPIC_VERSION)
                .post(RequestBody.create(objectMapper.writeValueAsString(payload), JSON))
                .build();
        } catch (Exception exception) {
            throw new NonTransientAiException("Anthropic Messages request serialization failed", exception);
        }
    }

    private void requireSuccess(Response response) {
        if (response.isSuccessful()) {
            return;
        }
        if (response.code() == 429 || response.code() >= 500) {
            throw new TransientAiException("Anthropic Messages returned HTTP " + response.code());
        }
        throw new NonTransientAiException("Anthropic Messages returned HTTP " + response.code());
    }

    private ChatResponse response(String text, JsonNode usage) {
        if (usage == null || usage.isMissingNode() || usage.isNull()) {
            return response(text, 0, 0, false);
        }
        return response(
            text,
            usage.path("input_tokens").asInt(0),
            usage.path("output_tokens").asInt(0),
            true
        );
    }

    private ChatResponse response(String text, int inputTokens, int outputTokens) {
        return response(text, inputTokens, outputTokens, true);
    }

    private ChatResponse response(String text, int inputTokens, int outputTokens, boolean hasUsage) {
        ChatResponseMetadata.Builder metadata = ChatResponseMetadata.builder().model(model);
        if (hasUsage) {
            metadata.usage(new DefaultUsage(inputTokens, outputTokens, inputTokens + outputTokens));
        }
        return new ChatResponse(
            List.of(new Generation(new AssistantMessage(text))),
            metadata.build()
        );
    }
}
