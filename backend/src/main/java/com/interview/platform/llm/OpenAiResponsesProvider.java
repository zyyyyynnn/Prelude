package com.interview.platform.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.interview.shared.api.BusinessException;
import com.interview.shared.api.LlmServerException;
import com.interview.shared.api.LlmTimeoutException;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

@Slf4j
@Component
public class OpenAiResponsesProvider implements LlmProvider {

    public static final String PROVIDER_KEY = "openai-responses";
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private final ObjectMapper objectMapper;
    private final LlmMetricsTracker metricsTracker;
    private final CustomLlmHttpClient httpClient;

    public OpenAiResponsesProvider(
        ObjectMapper objectMapper,
        LlmMetricsTracker metricsTracker,
        CustomLlmHttpClient httpClient
    ) {
        this.objectMapper = objectMapper;
        this.metricsTracker = metricsTracker;
        this.httpClient = httpClient;
    }

    @Override public String providerKey() { return PROVIDER_KEY; }
    @Override public String providerName() { return "OpenAI Responses"; }
    @Override public String defaultModel() { return ""; }
    @Override public String systemApiKey() { return ""; }

    @Override
    public String chat(LlmInvocation invocation) {
        return invoke(invocation, false, null);
    }

    @Override
    public void streamChat(LlmInvocation invocation, Consumer<String> onDelta) {
        invoke(invocation, true, onDelta);
    }

    private String invoke(LlmInvocation invocation, boolean stream, Consumer<String> onDelta) {
        if (invocation.apiKey() == null || invocation.apiKey().isBlank()) {
            throw BusinessException.badRequest("OpenAI Responses API Key 未配置");
        }
        if (invocation.baseUrl() == null || invocation.baseUrl().isBlank()) {
            throw BusinessException.badRequest("OpenAI Responses API 端点未配置");
        }

        Map<String, Object> payload = buildPayload(invocation, stream);
        long startTime = System.nanoTime();
        try {
            Request request = new Request.Builder()
                .url(invocation.baseUrl())
                .addHeader("Authorization", "Bearer " + invocation.apiKey())
                .post(RequestBody.create(objectMapper.writeValueAsString(payload), JSON))
                .build();
            try (Response response = httpClient.execute(request, Duration.ofSeconds(120))) {
                if (!response.isSuccessful()) {
                    log.warn("OpenAI Responses API returned status {}", response.code());
                    if (response.code() >= 500) {
                        metricsTracker.recordFailure(providerKey());
                        throw new LlmServerException("OpenAI Responses 服务端错误，状态码：" + response.code());
                    }
                    throw BusinessException.badRequest("OpenAI Responses 调用失败：" + response.code());
                }

                metricsTracker.recordLatency(providerKey(), System.nanoTime() - startTime);
                if (!stream) {
                    String body = httpClient.readBody(response.body());
                    recordUsage(objectMapper.readTree(body).path("usage"));
                    return extractContent(body);
                }
                if (response.body() == null) {
                    throw BusinessException.badRequest("OpenAI Responses 流式响应为空");
                }
                try (BufferedReader reader = httpClient.openStreamReader(response.body())) {
                    readStream(reader, onDelta);
                }
                return "";
            }
        } catch (BusinessException exception) {
            throw exception;
        } catch (IOException exception) {
            metricsTracker.recordFailure(providerKey());
            throw new LlmTimeoutException("OpenAI Responses 网络调用超时或异常，请检查配置");
        }
    }

    private Map<String, Object> buildPayload(LlmInvocation invocation, boolean stream) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", invocation.model());
        payload.put("input", invocation.messages());
        payload.put("stream", stream);
        if (invocation.maxTokens() != null) {
            payload.put("max_output_tokens", invocation.maxTokens());
        }
        if (invocation.extraParams() == null) {
            return payload;
        }

        Object thinkingDepth = invocation.extraParams().get("thinking_depth");
        if (thinkingDepth != null) {
            payload.put("reasoning", Map.of("effort", thinkingDepth));
        }
        Object responseFormat = invocation.extraParams().get("response_format");
        if (responseFormat != null) {
            payload.put("text", Map.of("format", responseFormat));
        }
        return payload;
    }

    private String extractContent(String body) throws IOException {
        JsonNode root = objectMapper.readTree(body);
        JsonNode convenienceText = root.get("output_text");
        if (convenienceText != null && convenienceText.isTextual() && !convenienceText.asText().isBlank()) {
            return convenienceText.asText();
        }

        StringBuilder content = new StringBuilder();
        JsonNode output = root.path("output");
        if (output.isArray()) {
            for (JsonNode item : output) {
                JsonNode parts = item.path("content");
                if (!parts.isArray()) {
                    continue;
                }
                for (JsonNode part : parts) {
                    String type = part.path("type").asText();
                    JsonNode text = "refusal".equals(type) ? part.get("refusal") : part.get("text");
                    if (text != null && text.isTextual()) {
                        content.append(text.asText());
                    }
                }
            }
        }
        if (content.isEmpty()) {
            throw BusinessException.badRequest("OpenAI Responses 返回内容为空");
        }
        return content.toString();
    }

    private void readStream(BufferedReader reader, Consumer<String> onDelta) throws IOException {
        String line;
        while ((line = httpClient.readStreamLine(reader)) != null) {
            String trimmed = line.trim();
            if (!trimmed.startsWith("data:")) {
                continue;
            }
            String data = trimmed.substring("data:".length()).trim();
            if (data.isBlank() || "[DONE]".equals(data)) {
                continue;
            }
            JsonNode event = objectMapper.readTree(data);
            String type = event.path("type").asText();
            if ("response.output_text.delta".equals(type) || "response.refusal.delta".equals(type)) {
                String delta = event.path("delta").asText("");
                if (!delta.isBlank()) {
                    metricsTracker.recordTokens(providerKey(), delta.length() / 2.0);
                    onDelta.accept(delta);
                }
            } else if ("response.completed".equals(type)) {
                recordUsage(event.path("response").path("usage"));
            } else if ("response.failed".equals(type) || "response.incomplete".equals(type)) {
                JsonNode response = event.path("response");
                throw BusinessException.badRequest("OpenAI Responses 流式调用失败");
            } else if ("error".equals(type)) {
                throw BusinessException.badRequest("OpenAI Responses 流式调用失败");
            }
        }
    }

    private void recordUsage(JsonNode usage) {
        JsonNode totalTokens = usage.path("total_tokens");
        if (totalTokens.isNumber()) {
            metricsTracker.recordTokens(providerKey(), totalTokens.asDouble());
        }
    }
}
