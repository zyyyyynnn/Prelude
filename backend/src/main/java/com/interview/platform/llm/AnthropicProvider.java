package com.interview.platform.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.interview.shared.api.BusinessException;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import com.interview.shared.api.LlmServerException;
import com.interview.shared.api.LlmTimeoutException;

@Slf4j
@Component
public class AnthropicProvider implements LlmProvider {

    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private static final String ANTHROPIC_VERSION = "2023-06-01";
    private static final int DEFAULT_MAX_TOKENS = 4096;

    private final ObjectMapper objectMapper;
    private final OkHttpClient client;
    private final LlmMetricsTracker metricsTracker;

    public AnthropicProvider(
        ObjectMapper objectMapper,
        LlmMetricsTracker metricsTracker
    ) {
        this.objectMapper = objectMapper;
        this.metricsTracker = metricsTracker;
        this.client = new OkHttpClient.Builder()
            .connectTimeout(Duration.ofSeconds(15))
            .readTimeout(Duration.ofSeconds(120))
            .writeTimeout(Duration.ofSeconds(30))
            .build();
    }

    @Override public String providerKey()  { return CustomLlmProtocol.ANTHROPIC_MESSAGES.providerKey(); }
    @Override public String providerName() { return "Anthropic Messages"; }
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
        String apiKey = invocation.apiKey();
        if (apiKey == null || apiKey.isBlank()) {
            throw BusinessException.badRequest("Anthropic API Key 未配置");
        }

        List<Map<String, String>> allMessages = invocation.messages();
        String systemContent = allMessages.stream()
            .filter(m -> "system".equals(m.get("role")))
            .map(m -> m.getOrDefault("content", ""))
            .filter(c -> !c.isBlank())
            .collect(Collectors.joining("\n\n"));
        List<Map<String, String>> dialogMessages = allMessages.stream()
            .filter(m -> !"system".equals(m.get("role")))
            .toList();

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", invocation.model());
        payload.put("max_tokens", invocation.maxTokens() != null ? invocation.maxTokens() : DEFAULT_MAX_TOKENS);
        payload.put("stream", stream);
        if (!systemContent.isBlank()) {
            payload.put("system", systemContent);
        }
        payload.put("messages", dialogMessages);

        long startTime = System.nanoTime();
        try {
            if (invocation.baseUrl() == null || invocation.baseUrl().isBlank()) {
                throw BusinessException.badRequest("Anthropic API 端点未配置");
            }
            Request request = new Request.Builder()
                .url(invocation.baseUrl())
                .addHeader("x-api-key", apiKey)
                .addHeader("anthropic-version", ANTHROPIC_VERSION)
                .post(RequestBody.create(objectMapper.writeValueAsString(payload), JSON))
                .build();

            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    String body = response.body() == null ? "" : response.body().string();
                    log.warn("Anthropic API error {}: {}", response.code(), body);
                    if (response.code() >= 500) {
                        metricsTracker.recordFailure(providerKey());
                        throw new LlmServerException("Anthropic 服务端错误，状态码：" + response.code());
                    } else {
                        throw BusinessException.badRequest("Anthropic 调用失败：" + response.code());
                    }
                }
                
                metricsTracker.recordLatency(providerKey(), System.nanoTime() - startTime);

                if (!stream) {
                    String body = response.body() == null ? "" : response.body().string();
                    try {
                        JsonNode root = objectMapper.readTree(body);
                        JsonNode inputTokens = root.at("/usage/input_tokens");
                        JsonNode outputTokens = root.at("/usage/output_tokens");
                        double total = 0;
                        if (!inputTokens.isMissingNode() && inputTokens.isNumber()) {
                            total += inputTokens.asDouble();
                        }
                        if (!outputTokens.isMissingNode() && outputTokens.isNumber()) {
                            total += outputTokens.asDouble();
                        }
                        metricsTracker.recordTokens(providerKey(), total);
                    } catch (Exception e) {
                        log.debug("Failed to parse token usage from Anthropic response", e);
                    }
                    return extractContent(body);
                }
                if (response.body() == null) {
                    throw BusinessException.badRequest("Anthropic 流式响应为空");
                }
                try (BufferedReader reader = new BufferedReader(response.body().charStream())) {
                    return readStream(reader, onDelta);
                }
            }
        } catch (BusinessException e) {
            throw e;
        } catch (IOException e) {
            metricsTracker.recordFailure(providerKey());
            throw new LlmTimeoutException("Anthropic 网络调用超时或异常，请检查配置");
        }
    }

    private String readStream(BufferedReader reader, Consumer<String> onDelta) throws IOException {
        String currentEvent = null;
        String line;
        while ((line = reader.readLine()) != null) {
            String trimmed = line.trim();
            if (trimmed.startsWith("event:")) {
                currentEvent = trimmed.substring("event:".length()).trim();
                if ("message_stop".equals(currentEvent)) {
                    break;
                }
            } else if (trimmed.startsWith("data:") && "content_block_delta".equals(currentEvent)) {
                String data = trimmed.substring("data:".length()).trim();
                String delta = extractDeltaText(data);
                if (!delta.isBlank()) {
                    metricsTracker.recordTokens(providerKey(), delta.length() / 2.0);
                    onDelta.accept(delta);
                }
            }
        }
        return "";
    }

    private String extractContent(String body) throws IOException {
        JsonNode root = objectMapper.readTree(body);
        JsonNode textNode = root.at("/content/0/text");
        if (textNode.isMissingNode() || textNode.asText().isBlank()) {
            throw BusinessException.badRequest("Anthropic 返回内容为空");
        }
        return textNode.asText();
    }

    private String extractDeltaText(String data) {
        try {
            JsonNode root = objectMapper.readTree(data);
            JsonNode textNode = root.at("/delta/text");
            return textNode.isMissingNode() || textNode.isNull() ? "" : textNode.asText();
        } catch (IOException e) {
            log.debug("Failed to parse Anthropic stream delta", e);
            return "";
        }
    }
}
