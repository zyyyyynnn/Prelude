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
    void discoversModelsFromOpenAiCompatibleEndpoint() throws Exception {
        startServer(200, """
            {"object":"list","data":[{"id":"model-b"},{"id":"model-a"},{"id":"model-a"}]}
            """);
        LlmModelDiscoveryServiceImpl service = new LlmModelDiscoveryServiceImpl();

        LlmModelDiscoveryResponse response = service.discoverModels(
            new LlmModelDiscoveryRequest(baseUrl(), "sk-test")
        );

        assertThat(response.providerKey()).isEqualTo("openai-compatible");
        assertThat(response.baseUrl()).isEqualTo(baseUrl());
        assertThat(response.models()).containsExactly("model-b", "model-a");
    }

    @Test
    void mapsUnauthorizedResponseToReadableError() throws Exception {
        startServer(401, "{\"error\":{\"message\":\"bad key\"}}");
        LlmModelDiscoveryServiceImpl service = new LlmModelDiscoveryServiceImpl();

        assertThatThrownBy(() -> service.discoverModels(new LlmModelDiscoveryRequest(baseUrl(), "sk-test")))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("鉴权失败");
    }

    @Test
    void rejectsEmptyOrIncompatibleModelList() throws Exception {
        startServer(200, "{\"object\":\"list\",\"data\":[]}");
        LlmModelDiscoveryServiceImpl service = new LlmModelDiscoveryServiceImpl();

        assertThatThrownBy(() -> service.discoverModels(new LlmModelDiscoveryRequest(baseUrl(), "sk-test")))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("未检测到模型列表");
    }

    private void startServer(int status, String body) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/models", exchange -> writeResponse(exchange, status, body));
        server.start();
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
