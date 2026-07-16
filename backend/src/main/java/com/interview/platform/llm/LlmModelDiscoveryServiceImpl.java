package com.interview.platform.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.interview.shared.api.BusinessException;
import com.interview.platform.llm.api.LlmModelDiscoveryRequest;
import com.interview.platform.llm.api.LlmModelDiscoveryResponse;
import lombok.extern.slf4j.Slf4j;
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
    private final CustomLlmHttpClient httpClient;
    private final CustomLlmEgressPolicy egressPolicy;

    public LlmModelDiscoveryServiceImpl(
        ObjectMapper objectMapper,
        CustomLlmHttpClient httpClient,
        CustomLlmEgressPolicy egressPolicy
    ) {
        this.objectMapper = objectMapper;
        this.httpClient = httpClient;
        this.egressPolicy = egressPolicy;
    }

    @Override
    public LlmModelDiscoveryResponse discoverModels(LlmModelDiscoveryRequest request) {
        CustomLlmProtocol protocol = CustomLlmProtocol.require(request.providerKey());
        if (!protocol.supportsModelDiscovery()) {
            throw BusinessException.badRequest("当前协议不支持自动检测模型");
        }
        String baseUrl = CustomLlmEndpointUrl.normalizeRoot(request.baseUrl(), protocol);
        if (request.apiKey() == null || request.apiKey().isBlank()) {
            throw BusinessException.badRequest("API Key 不能为空");
        }

        String modelsUrl = CustomLlmEndpointUrl.toModelsUrl(baseUrl, protocol);
        egressPolicy.validateConfiguredEndpoint(modelsUrl);
        Request httpRequest = new Request.Builder()
            .url(modelsUrl)
            .addHeader("Authorization", "Bearer " + request.apiKey())
            .get()
            .build();

        try (Response response = httpClient.execute(httpRequest, Duration.ofSeconds(15))) {
            String body = httpClient.readBody(response.body());
            if (response.code() == 401 || response.code() == 403) {
                throw BusinessException.badRequest("鉴权失败，请检查 API Key");
            }
            if (!response.isSuccessful()) {
                throw BusinessException.badRequest("Base URL 不可达或模型列表接口返回异常：" + response.code());
            }
            List<String> models = parseModelsResponse(body);
            return new LlmModelDiscoveryResponse(protocol.providerKey(), baseUrl, models);
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
