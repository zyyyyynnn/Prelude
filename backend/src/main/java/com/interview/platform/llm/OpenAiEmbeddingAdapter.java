package com.interview.platform.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.interview.shared.api.BusinessException;
import com.interview.platform.llm.EmbedPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class OpenAiEmbeddingAdapter implements EmbedPort {

    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private final OkHttpClient client = new OkHttpClient();
    private final ObjectMapper objectMapper;

    @Value("${openai.api-key:}")
    private String apiKey;

    @Value("${openai.embedding-url:https://api.openai.com/v1/embeddings}")
    private String embeddingUrl;

    @Value("${openai.embedding-model:text-embedding-3-small}")
    private String embeddingModel;

    @Value("${app.dev-fixtures.enabled:false}")
    private boolean devFixtureEnabled;

    @Override
    public float[] embed(String text) {
        if (devFixtureEnabled || apiKey == null || apiKey.isBlank() || apiKey.startsWith("${")) {
            // Predictable mock embedding based on character hash code to support offline/dev-fixture testing
            float[] mock = new float[1536];
            int hash = text.hashCode();
            for (int i = 0; i < 1536; i++) {
                mock[i] = (float) Math.sin(hash + i);
            }
            return mock;
        }

        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("input", text);
            payload.put("model", embeddingModel);

            Request request = new Request.Builder()
                .url(embeddingUrl)
                .addHeader("Authorization", "Bearer " + apiKey)
                .post(RequestBody.create(objectMapper.writeValueAsString(payload), JSON))
                .build();

            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    throw BusinessException.badRequest("Embedding API call failed: " + response.code());
                }
                String body = response.body() != null ? response.body().string() : "";
                JsonNode root = objectMapper.readTree(body);
                JsonNode embeddingNode = root.at("/data/0/embedding");
                if (embeddingNode.isMissingNode() || !embeddingNode.isArray()) {
                    throw BusinessException.badRequest("Invalid response from Embedding API");
                }
                float[] vector = new float[embeddingNode.size()];
                for (int i = 0; i < embeddingNode.size(); i++) {
                    vector[i] = (float) embeddingNode.get(i).asDouble();
                }
                return vector;
            }
        } catch (IOException e) {
            log.error("Failed to get embedding from API", e);
            throw BusinessException.badRequest("获取 Embedding 失败");
        }
    }
}
