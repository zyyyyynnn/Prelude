package com.prelude.llm;

import com.prelude.llm.api.ModelCapabilityResponse;
import com.prelude.llm.api.ModelCapabilityResponse.ReasoningLevel;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import okhttp3.Dns;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class CustomModelCapabilityDiscoveryTest {

    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void openAiResponsesKeepsOnlyLevelsWhoseReasoningParameterIsAccepted() throws Exception {
        AtomicInteger requests = new AtomicInteger();
        start("/v1/responses", exchange -> {
            requests.incrementAndGet();
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            if (body.contains("\"effort\":\"prelude_probe_invalid\"")
                || body.contains("\"effort\":\"medium\"")) {
                respond(exchange, 400, "{\"error\":{\"message\":\"unsupported effort\"}}");
            } else {
                respond(exchange, 200, "{\"output_text\":\"OK\"}");
            }
        });

        ModelCapabilityResponse capability = discovery().discover(
            "openai-responses", root(), "account-key", "account-model");

        assertThat(requests).hasValue(4);
        assertThat(capability.reasoning()).isTrue();
        assertThat(capability.supportedReasoningLevels())
            .containsExactly(ReasoningLevel.AUTO, ReasoningLevel.LOW, ReasoningLevel.HIGH);
    }

    @Test
    void openAiProbeFailureFallsBackToAutoOnlyInsteadOfInventingCapabilities() throws Exception {
        AtomicInteger requests = new AtomicInteger();
        start("/v1/chat/completions", exchange -> {
            requests.incrementAndGet();
            respond(exchange, 500, "{\"error\":{\"message\":\"temporary\"}}");
        });

        ModelCapabilityResponse capability = discovery().discover(
            "openai-chat-completions", root(), "account-key", "account-model");

        assertThat(requests).hasValue(1);
        assertThat(capability.reasoning()).isFalse();
        assertThat(capability.supportedReasoningLevels()).containsExactly(ReasoningLevel.AUTO);
    }

    @Test
    void openAiEndpointThatSilentlyIgnoresReasoningParametersStaysAutoOnly() throws Exception {
        AtomicInteger requests = new AtomicInteger();
        start("/v1/chat/completions", exchange -> {
            requests.incrementAndGet();
            respond(exchange, 200, "{\"choices\":[]}");
        });

        ModelCapabilityResponse capability = discovery().discover(
            "openai-chat-completions", root(), "account-key", "account-model");

        assertThat(requests).hasValue(1);
        assertThat(capability.reasoning()).isFalse();
        assertThat(capability.supportedReasoningLevels()).containsExactly(ReasoningLevel.AUTO);
    }

    @Test
    void anthropicUsesUpstreamModelCapabilityMetadataWithoutModelNameGuessing() throws Exception {
        start("/v1/models/account-model", exchange -> {
            assertThat(exchange.getRequestHeaders().getFirst("x-api-key")).isEqualTo("account-key");
            assertThat(exchange.getRequestHeaders().getFirst("anthropic-version")).isEqualTo("2023-06-01");
            respond(exchange, 200, """
                {
                  "id":"account-model",
                  "capabilities":{
                    "effort":{
                      "supported":true,
                      "low":{"supported":true},
                      "medium":{"supported":false},
                      "high":{"supported":true}
                    }
                  }
                }
                """);
        });

        ModelCapabilityResponse capability = discovery().discover(
            "anthropic-messages", root(), "account-key", "account-model");

        assertThat(capability.reasoning()).isTrue();
        assertThat(capability.supportedReasoningLevels())
            .containsExactly(ReasoningLevel.AUTO, ReasoningLevel.LOW, ReasoningLevel.HIGH);
    }

    private CustomModelCapabilityDiscovery discovery() {
        int port = server.getAddress().getPort();
        CustomLlmEgressPolicy policy = new CustomLlmEgressPolicy(
            true, true, Set.of(port), Dns.SYSTEM);
        EgressHttpClientFactory clients = new EgressHttpClientFactory(policy);
        return new CustomModelCapabilityDiscovery(
            new ModelCapabilityCatalog(), policy, clients, new ObjectMapper());
    }

    private String root() {
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/v1";
    }

    private void start(String path, com.sun.net.httpserver.HttpHandler handler) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext(path, handler);
        server.start();
    }

    private void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (var output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }
}
