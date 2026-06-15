package com.interview.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.interview.common.BusinessException;
import com.interview.dto.LlmModelDiscoveryRequest;
import com.interview.dto.LlmModelDiscoveryResponse;
import com.interview.llm.OpenAiCompatibleProvider;
import com.interview.llm.OpenAiCompatibleUrl;
import com.interview.service.LlmModelDiscoveryService;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

@Slf4j
@Service
public class LlmModelDiscoveryServiceImpl implements LlmModelDiscoveryService {

    private final ObjectMapper objectMapper;
    private final OkHttpClient client;

    public LlmModelDiscoveryServiceImpl() {
        this(new ObjectMapper());
    }

    public LlmModelDiscoveryServiceImpl(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.client = new OkHttpClient.Builder()
            .connectTimeout(Duration.ofSeconds(10))
            .readTimeout(Duration.ofSeconds(15))
            .writeTimeout(Duration.ofSeconds(10))
            .build();
    }

    @Override
    public LlmModelDiscoveryResponse discoverModels(LlmModelDiscoveryRequest request) {
        String baseUrl = OpenAiCompatibleUrl.normalizeRoot(request.baseUrl());
        if (request.apiKey() == null || request.apiKey().isBlank()) {
            throw BusinessException.badRequest("API Key 不能为空");
        }

        Request httpRequest = new Request.Builder()
            .url(OpenAiCompatibleUrl.toModelsUrl(baseUrl))
            .addHeader("Authorization", "Bearer " + request.apiKey())
            .get()
            .build();

        try (Response response = client.newCall(httpRequest).execute()) {
            String body = response.body() == null ? "" : response.body().string();
            if (response.code() == 401 || response.code() == 403) {
                throw BusinessException.badRequest("鉴权失败，请检查 API Key");
            }
            if (!response.isSuccessful()) {
                throw BusinessException.badRequest("Base URL 不可达或模型列表接口返回异常：" + response.code());
            }
            List<String> models = parseModelsResponse(body);
            return new LlmModelDiscoveryResponse(OpenAiCompatibleProvider.PROVIDER_KEY, baseUrl, models);
        } catch (BusinessException exception) {
            throw exception;
        } catch (IOException exception) {
            log.warn("Failed to discover OpenAI-compatible models from endpoint {}", baseUrl);
            throw BusinessException.badRequest("Base URL 不可达，请检查 Base URL");
        }
    }

    private List<String> parseModelsResponse(String body) {
        try {
            JsonNode data = objectMapper.readTree(body).get("data");
            if (data == null || !data.isArray()) {
                throw BusinessException.badRequest("响应格式不兼容，未找到模型列表");
            }
            LinkedHashSet<String> models = new LinkedHashSet<>();
            for (JsonNode item : data) {
                JsonNode id = item.get("id");
                if (id != null && id.isTextual() && !id.asText().isBlank()) {
                    models.add(id.asText());
                }
            }
            if (models.isEmpty()) {
                throw BusinessException.badRequest("未检测到模型列表");
            }
            return new ArrayList<>(models);
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw BusinessException.badRequest("响应格式不兼容，无法解析模型列表");
        }
    }
}
