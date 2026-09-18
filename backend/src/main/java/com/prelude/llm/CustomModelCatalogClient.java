package com.prelude.llm;

import com.prelude.BusinessException;
import com.prelude.llm.api.ModelCapabilityResponse;
import lombok.RequiredArgsConstructor;
import okhttp3.Request;
import okhttp3.Response;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Protocol-specific catalog HTTP for custom endpoints. Keeps OkHttp out of
 * the profile configuration service.
 */
@Component
@RequiredArgsConstructor
class CustomModelCatalogClient {

    private static final String MODELS_PATH = "/models";

    private final CustomLlmEgressPolicy egressPolicy;
    private final EgressHttpClientFactory egressHttpClientFactory;
    private final ModelCapabilityCatalog capabilityCatalog;
    private final ObjectMapper objectMapper;

    List<ModelCapabilityResponse> listModels(CustomLlmProtocol protocol, String baseUrl, String apiKey) {
        String modelsUrl = baseUrl + (protocol == CustomLlmProtocol.ANTHROPIC_MESSAGES
            ? "/v1" + MODELS_PATH
            : MODELS_PATH);
        egressPolicy.validateConfiguredEndpoint(modelsUrl);
        Request.Builder requestBuilder = new Request.Builder().url(modelsUrl).get();
        if (protocol == CustomLlmProtocol.ANTHROPIC_MESSAGES) {
            requestBuilder
                .addHeader("x-api-key", apiKey)
                .addHeader("anthropic-version", "2023-06-01");
        } else {
            requestBuilder.addHeader("Authorization", "Bearer " + apiKey);
        }
        try (Response response = egressHttpClientFactory.discoveryClient()
            .newCall(requestBuilder.build()).execute()) {
            if (response.code() == 401 || response.code() == 403) {
                throw BusinessException.badRequest("鉴权失败，请检查 API Key");
            }
            if (!response.isSuccessful()) {
                throw BusinessException.badRequest("Base URL 不可达或模型列表接口返回异常：" + response.code());
            }
            String body = response.body() == null ? "" : response.body().string();
            return parseModels(protocol.providerKey(), body);
        } catch (BusinessException exception) {
            throw exception;
        } catch (IOException exception) {
            throw BusinessException.badRequest("Base URL 不可达，请检查 Base URL");
        }
    }

    private List<ModelCapabilityResponse> parseModels(String provider, String body) {
        try {
            List<ModelCapabilityResponse> models = new ArrayList<>();
            var root = objectMapper.readTree(body);
            for (var node : root.path("data")) {
                String id = node.path("id").asString(null);
                if (id != null && !id.isBlank()) {
                    models.add(capabilityCatalog.capability(provider, id));
                }
            }
            return List.copyOf(models);
        } catch (Exception exception) {
            throw BusinessException.badRequest("模型列表响应格式不正确");
        }
    }
}
