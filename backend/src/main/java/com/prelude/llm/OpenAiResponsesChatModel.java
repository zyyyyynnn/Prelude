package com.prelude.llm;

import com.prelude.llm.api.ModelCapabilityResponse;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
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

/**
 * Narrow Spring AI ChatModel adapter for the OpenAI Responses wire protocol.
 * Spring AI 2.0.1 does not expose a Responses ChatModel, so only this missing
 * protocol seam is adapted; retry remains owned by ModelExecutionService.
 */
final class OpenAiResponsesChatModel implements ChatModel {

    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private final String endpoint;
    private final String apiKey;
    private final String model;
    private final ModelCapabilityResponse.ReasoningLevel reasoningLevel;
    private final okhttp3.OkHttpClient httpClient;
    private final ObjectMapper objectMapper;

    OpenAiResponsesChatModel(
        String root,
        String apiKey,
        String model,
        String reasoningLevel,
        okhttp3.OkHttpClient httpClient,
        ObjectMapper objectMapper
    ) {
        this.endpoint = root + CustomLlmProtocol.OPENAI_RESPONSES.endpointSuffix();
        this.apiKey = apiKey;
        this.model = model;
        this.reasoningLevel = ModelCapabilityResponse.ReasoningLevel.valueOf(reasoningLevel);
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public ChatResponse call(Prompt prompt) {
        Request request = request(prompt, false);
        try (Response response = httpClient.newCall(request).execute()) {
            requireSuccess(response);
            String body = response.body() == null ? "" : response.body().string();
            JsonNode root = objectMapper.readTree(body);
            return response(extractText(root), root.path("usage"));
        } catch (IOException exception) {
            throw new TransientAiException("OpenAI Responses transport failure", exception);
        }
    }

    @Override
    public Flux<ChatResponse> stream(Prompt prompt) {
        return Flux.create(sink -> {
            Request request = request(prompt, true);
            try (Response response = httpClient.newCall(request).execute()) {
                requireSuccess(response);
                if (response.body() == null) {
                    throw new TransientAiException("OpenAI Responses stream returned no body");
                }
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                    response.body().byteStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null && !sink.isCancelled()) {
                        String trimmed = line.trim();
                        if (!trimmed.startsWith("data:")) {
                            continue;
                        }
                        String data = trimmed.substring("data:".length()).trim();
                        if (data.isBlank() || "[DONE]".equals(data)) {
                            continue;
                        }
                        JsonNode event = objectMapper.readTree(data);
                        String type = event.path("type").asString("");
                        if ("response.output_text.delta".equals(type)
                            || "response.refusal.delta".equals(type)) {
                            String delta = event.path("delta").asString("");
                            if (!delta.isEmpty()) {
                                sink.next(response(delta, null));
                            }
                        } else if ("response.completed".equals(type)) {
                            JsonNode completed = event.path("response");
                            sink.next(response("", completed.path("usage")));
                        } else if ("response.failed".equals(type)
                            || "response.incomplete".equals(type)
                            || "error".equals(type)) {
                            throw new NonTransientAiException("OpenAI Responses stream failed");
                        }
                    }
                }
                sink.complete();
            } catch (IOException exception) {
                sink.error(new TransientAiException("OpenAI Responses stream transport failure", exception));
            } catch (RuntimeException exception) {
                sink.error(exception);
            }
        });
    }

    private Request request(Prompt prompt, boolean stream) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", model);
        payload.put("input", prompt.getInstructions().stream().map(this::inputMessage).toList());
        payload.put("stream", stream);
        if (reasoningLevel != ModelCapabilityResponse.ReasoningLevel.AUTO) {
            payload.put("reasoning", Map.of(
                "effort", reasoningLevel.name().toLowerCase(java.util.Locale.ROOT)));
        }
        try {
            return new Request.Builder()
                .url(endpoint)
                .addHeader("Authorization", "Bearer " + apiKey)
                .post(RequestBody.create(objectMapper.writeValueAsString(payload), JSON))
                .build();
        } catch (Exception exception) {
            throw new NonTransientAiException("OpenAI Responses request serialization failed", exception);
        }
    }

    private Map<String, String> inputMessage(Message message) {
        return Map.of(
            "role", message.getMessageType().getValue(),
            "content", message.getText()
        );
    }

    private void requireSuccess(Response response) {
        if (response.isSuccessful()) {
            return;
        }
        if (response.code() == 429 || response.code() >= 500) {
            throw new TransientAiException("OpenAI Responses returned HTTP " + response.code());
        }
        throw new NonTransientAiException("OpenAI Responses returned HTTP " + response.code());
    }

    private String extractText(JsonNode root) {
        String outputText = root.path("output_text").asString("");
        if (!outputText.isBlank()) {
            return outputText;
        }
        StringBuilder content = new StringBuilder();
        for (JsonNode output : root.path("output")) {
            for (JsonNode part : output.path("content")) {
                String text = part.path("text").asString("");
                if (text.isBlank()) {
                    text = part.path("refusal").asString("");
                }
                content.append(text);
            }
        }
        if (content.isEmpty()) {
            throw new NonTransientAiException("OpenAI Responses returned no text");
        }
        return content.toString();
    }

    private ChatResponse response(String text, JsonNode usage) {
        ChatResponseMetadata.Builder metadata = ChatResponseMetadata.builder().model(model);
        if (usage != null && !usage.isMissingNode() && !usage.isNull()) {
            int input = usage.path("input_tokens").asInt(0);
            int output = usage.path("output_tokens").asInt(0);
            int total = usage.path("total_tokens").asInt(input + output);
            metadata.usage(new DefaultUsage(input, output, total));
        }
        return new ChatResponse(
            List.of(new Generation(new AssistantMessage(text))),
            metadata.build()
        );
    }
}
