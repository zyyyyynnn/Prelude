package com.interview.platform.llm;

import com.interview.platform.llm.LlmModelDiscoveryServiceImpl;

import com.interview.shared.api.BusinessException;
import com.interview.platform.llm.api.LlmModelDiscoveryRequest;
import com.interview.platform.llm.api.LlmModelDiscoveryResponse;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LlmModelDiscoveryServiceImplTest {

    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void discoversModelsFromOpenAiProtocolEndpoint() throws Exception {
        startServer(200, """
            {"object":"list","data":[{"id":"model-b"},{"id":"model-a"},{"id":"model-a"}]}
            """);
        LlmModelDiscoveryServiceImpl service = service();

        LlmModelDiscoveryResponse response = service.discoverModels(
            new LlmModelDiscoveryRequest("openai-responses", baseUrl(), "sk-test")
        );

        assertThat(response.providerKey()).isEqualTo("openai-responses");
        assertThat(response.baseUrl()).isEqualTo(baseUrl());
        assertThat(response.models()).containsExactly("model-b", "model-a");
    }

    @Test
    void mapsUnauthorizedResponseToReadableError() throws Exception {
        startServer(401, "{\"error\":{\"message\":\"bad key\"}}");
        LlmModelDiscoveryServiceImpl service = service();

        assertThatThrownBy(() -> service.discoverModels(
            new LlmModelDiscoveryRequest("openai-chat-completions", baseUrl(), "sk-test")))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("鉴权失败");
    }

    @Test
    void rejectsEmptyOrIncompatibleModelList() throws Exception {
        startServer(200, "{\"object\":\"list\",\"data\":[]}");
        LlmModelDiscoveryServiceImpl service = service();

        assertThatThrownBy(() -> service.discoverModels(
            new LlmModelDiscoveryRequest("openai-responses", baseUrl(), "sk-test")))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("未检测到模型列表");
    }

    @Test
    void rejectsModelDiscoveryForAnthropicMessages() {
        CustomLlmEgressPolicy policy = new CustomLlmEgressPolicy(
            false, false, java.util.Set.of(443), okhttp3.Dns.SYSTEM);
        LlmModelDiscoveryServiceImpl service = new LlmModelDiscoveryServiceImpl(
            new com.fasterxml.jackson.databind.ObjectMapper(), new CustomLlmHttpClient(policy), policy);

        assertThatThrownBy(() -> service.discoverModels(
            new LlmModelDiscoveryRequest("anthropic-messages", "https://example.com/v1", "sk-test")))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("不支持自动检测模型");
    }

    private void startServer(int status, String body) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/models", exchange -> writeResponse(exchange, status, body));
        server.start();
    }

    private LlmModelDiscoveryServiceImpl service() {
        CustomLlmEgressPolicy policy = CustomLlmTestClients.localPolicy(server.getAddress().getPort());
        return new LlmModelDiscoveryServiceImpl(
            new com.fasterxml.jackson.databind.ObjectMapper(), new CustomLlmHttpClient(policy), policy);
    }

    private String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/v1";
    }

    private void writeResponse(HttpExchange exchange, int status, String body) throws IOException {
        assertThat(exchange.getRequestMethod()).isEqualTo("GET");
        assertThat(exchange.getRequestHeaders().getFirst("Authorization")).isEqualTo("Bearer sk-test");
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
