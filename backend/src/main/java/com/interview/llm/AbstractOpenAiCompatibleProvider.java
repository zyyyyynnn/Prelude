package com.interview.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.interview.common.BusinessException;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import java.io.BufferedReader;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

@Slf4j
public abstract class AbstractOpenAiCompatibleProvider implements LlmProvider {

    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private final ObjectMapper objectMapper;
    private final String providerKey;
    private final String providerName;
    private final String defaultModel;
    private final String systemApiKey;
    private final OkHttpClient client;

    protected AbstractOpenAiCompatibleProvider(
        ObjectMapper objectMapper,
        String providerKey,
        String providerName,
        String defaultModel,
        String systemApiKey
    ) {
        this.objectMapper = objectMapper;
        this.providerKey = providerKey;
        this.providerName = providerName;
        this.defaultModel = defaultModel;
        this.systemApiKey = systemApiKey;
        this.client = new OkHttpClient.Builder()
            .connectTimeout(Duration.ofSeconds(15))
            .readTimeout(Duration.ofSeconds(60))
            .writeTimeout(Duration.ofSeconds(30))
            .build();
    }

    @Override
    public String providerKey() {
        return providerKey;
    }

    @Override
    public String providerName() {
        return providerName;
    }

    @Override
    public String defaultModel() {
        return defaultModel;
    }

    @Override
    public String systemApiKey() {
        return systemApiKey;
    }

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
            throw BusinessException.badRequest(providerName + " API Key 未配置");
        }
        try {
            Map<String, Object> payload = Map.of(
                "model", invocation.model(),
                "stream", stream,
                "messages", invocation.messages()
            );
            Request request = buildRequest(payload, invocation.baseUrl(), apiKey);
            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    String body = response.body() == null ? "" : response.body().string();
                    throw BusinessException.badRequest(providerName + " 调用失败：" + response.code());
                }
                if (!stream) {
                    String body = response.body() == null ? "" : response.body().string();
                    return extractContent(body);
                }
                if (response.body() == null) {
                    throw BusinessException.badRequest(providerName + " 流式响应为空");
                }
                try (BufferedReader reader = new BufferedReader(response.body().charStream())) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        String trimmed = line.trim();
                        if (!trimmed.startsWith("data:")) {
                            continue;
                        }
                        String data = trimmed.substring("data:".length()).trim();
                        if ("[DONE]".equals(data)) {
                            return "";
                        }
                        String delta = extractDeltaContent(data);
                        if (!delta.isBlank()) {
                            onDelta.accept(delta);
                        }
                    }
                }
                return "";
            }
        } catch (BusinessException exception) {
            throw exception;
        } catch (IOException exception) {
            throw BusinessException.badRequest(providerName + " 调用异常，请稍后重试");
        }
    }

    private Request buildRequest(Map<String, Object> payload, String baseUrl, String apiKey) throws IOException {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw BusinessException.badRequest(providerName + " API 端点未配置");
        }
        return new Request.Builder()
            .url(baseUrl)
            .addHeader("Authorization", "Bearer " + apiKey)
            .post(RequestBody.create(objectMapper.writeValueAsString(payload), JSON))
            .build();
    }

    private String extractContent(String body) throws IOException {
        JsonNode root = objectMapper.readTree(body);
        JsonNode contentNode = root.at("/choices/0/message/content");
        if (contentNode.isMissingNode() || contentNode.asText().isBlank()) {
            throw BusinessException.badRequest(providerName + " 返回内容为空");
        }
        return contentNode.asText();
    }

    private String extractDeltaContent(String data) {
        try {
            JsonNode root = objectMapper.readTree(data);
            JsonNode contentNode = root.at("/choices/0/delta/content");
            return contentNode.isMissingNode() || contentNode.isNull() ? "" : contentNode.asText();
        } catch (IOException exception) {
            log.debug("Failed to parse {} stream delta", providerName, exception);
            return "";
        }
    }
}
