package com.interview.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.interview.common.BusinessException;
import lombok.RequiredArgsConstructor;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

@Component
@RequiredArgsConstructor
public class LlmUtil {

    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private final ObjectMapper objectMapper;

    @Value("${deepseek.base-url}")
    private String baseUrl;

    @Value("${deepseek.model}")
    private String model;

    @Value("${deepseek.api-key}")
    private String apiKey;

    private final OkHttpClient client = new OkHttpClient.Builder()
        .connectTimeout(Duration.ofSeconds(15))
        .readTimeout(Duration.ofSeconds(60))
        .writeTimeout(Duration.ofSeconds(30))
        .build();

    public String chat(String systemPrompt, String userPrompt) {
        return chat(List.of(
            Map.of("role", "system", "content", systemPrompt),
            Map.of("role", "user", "content", userPrompt)
        ));
    }

    public String chat(List<Map<String, String>> messages) {
        if (apiKey == null || apiKey.isBlank() || "YOUR_KEY_HERE".equals(apiKey)) {
            throw BusinessException.badRequest("DeepSeek API Key 未配置，请先修改 application-local.yml");
        }

        Map<String, Object> payload = Map.of(
            "model", model,
            "stream", false,
            "messages", messages
        );

        Request request = buildRequest(payload);

        try (Response response = client.newCall(request).execute()) {
            String body = response.body() == null ? "" : response.body().string();
            if (!response.isSuccessful()) {
                throw BusinessException.badRequest("DeepSeek 调用失败：" + response.code());
            }
            JsonNode root = objectMapper.readTree(body);
            JsonNode contentNode = root.at("/choices/0/message/content");
            if (contentNode.isMissingNode() || contentNode.asText().isBlank()) {
                throw BusinessException.badRequest("DeepSeek 返回内容为空");
            }
            return contentNode.asText();
        } catch (IOException exception) {
            throw BusinessException.badRequest("DeepSeek 调用异常，请稍后重试");
        }
    }

    public void streamChat(List<Map<String, String>> messages, Consumer<String> onDelta) {
        if (apiKey == null || apiKey.isBlank() || "YOUR_KEY_HERE".equals(apiKey)) {
            throw BusinessException.badRequest("DeepSeek API Key 未配置，请先修改 application-local.yml");
        }

        Map<String, Object> payload = Map.of(
            "model", model,
            "stream", true,
            "messages", messages
        );

        Request request = buildRequest(payload);
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw BusinessException.badRequest("DeepSeek 流式调用失败：" + response.code());
            }
            if (response.body() == null) {
                throw BusinessException.badRequest("DeepSeek 流式响应为空");
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
                        return;
                    }
                    String delta = extractDeltaContent(data);
                    if (!delta.isBlank()) {
                        onDelta.accept(delta);
                    }
                }
            }
        } catch (IOException exception) {
            throw BusinessException.badRequest("DeepSeek 流式调用异常，请稍后重试");
        }
    }

    private Request buildRequest(Map<String, Object> payload) {
        try {
            return new Request.Builder()
                .url(baseUrl)
                .addHeader("Authorization", "Bearer " + apiKey)
                .post(RequestBody.create(objectMapper.writeValueAsString(payload), JSON))
                .build();
        } catch (IOException exception) {
            throw BusinessException.badRequest("LLM 请求构造失败");
        }
    }

    private String extractDeltaContent(String data) {
        try {
            JsonNode root = objectMapper.readTree(data);
            JsonNode contentNode = root.at("/choices/0/delta/content");
            return contentNode.isMissingNode() || contentNode.isNull() ? "" : contentNode.asText();
        } catch (IOException exception) {
            return "";
        }
    }
}
