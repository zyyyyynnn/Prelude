package com.interview.platform.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class OpenAiResponsesProviderTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void mapsInvocationToResponsesPayloadAndParsesOutput() throws Exception {
        AtomicReference<JsonNode> requestPayload = new AtomicReference<>();
        AtomicReference<String> authorization = new AtomicReference<>();
        startServer(exchange -> {
            requestPayload.set(objectMapper.readTree(exchange.getRequestBody()));
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            writeResponse(exchange, "application/json", """
                {"output":[{"type":"message","content":[{"type":"output_text","text":"OK"}]}],
                 "usage":{"total_tokens":12}}
                """);
        });

        OpenAiResponsesProvider provider = new OpenAiResponsesProvider(
            objectMapper,
            mock(LlmMetricsTracker.class),
            CustomLlmTestClients.localClient(server.getAddress().getPort())
        );
        String result = provider.chat(new LlmProvider.LlmInvocation(
            endpoint(), "gpt-test", "sk-test",
            List.of(Map.of("role", "user", "content", "hello")),
            1024,
            Map.of(
                "thinking_depth", "high",
                "response_format", Map.of("type", "json_object")
            )
        ));

        assertThat(result).isEqualTo("OK");
        assertThat(authorization.get()).isEqualTo("Bearer sk-test");
        assertThat(requestPayload.get().path("model").asText()).isEqualTo("gpt-test");
        assertThat(requestPayload.get().path("input").isArray()).isTrue();
        assertThat(requestPayload.get().path("max_output_tokens").asInt()).isEqualTo(1024);
        assertThat(requestPayload.get().at("/reasoning/effort").asText()).isEqualTo("high");
        assertThat(requestPayload.get().at("/text/format/type").asText()).isEqualTo("json_object");
        assertThat(requestPayload.get().has("messages")).isFalse();
        assertThat(requestPayload.get().has("max_tokens")).isFalse();
    }

    @Test
    void parsesResponsesStreamingEvents() throws Exception {
        startServer(exchange -> writeResponse(exchange, "text/event-stream", """
            event: response.output_text.delta
            data: {"type":"response.output_text.delta","delta":"O"}

            event: response.output_text.delta
            data: {"type":"response.output_text.delta","delta":"K"}

            event: response.completed
            data: {"type":"response.completed","response":{"usage":{"total_tokens":2}}}

            """));

        OpenAiResponsesProvider provider = new OpenAiResponsesProvider(
            objectMapper,
            mock(LlmMetricsTracker.class),
            CustomLlmTestClients.localClient(server.getAddress().getPort())
        );
        List<String> deltas = new ArrayList<>();
        provider.streamChat(new LlmProvider.LlmInvocation(
            endpoint(), "gpt-test", "sk-test",
            List.of(Map.of("role", "user", "content", "hello"))), deltas::add);

        assertThat(deltas).containsExactly("O", "K");
    }

    @Test
    void surfacesFailedStreamingResponses() throws Exception {
        startServer(exchange -> writeResponse(exchange, "text/event-stream", """
            event: response.failed
            data: {"type":"response.failed","response":{"error":{"message":"upstream failed"}}}

            """));

        OpenAiResponsesProvider provider = new OpenAiResponsesProvider(
            objectMapper,
            mock(LlmMetricsTracker.class),
            CustomLlmTestClients.localClient(server.getAddress().getPort())
        );

        assertThatThrownBy(() -> provider.streamChat(new LlmProvider.LlmInvocation(
            endpoint(), "gpt-test", "sk-test",
            List.of(Map.of("role", "user", "content", "hello"))), ignored -> { }))
            .hasMessageContaining("流式调用失败")
            .hasMessageNotContaining("upstream failed");
    }

    private void startServer(ExchangeHandler handler) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/responses", exchange -> {
            try {
                handler.handle(exchange);
            } finally {
                exchange.close();
            }
        });
        server.start();
    }

    private String endpoint() {
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/v1/responses";
    }

    private void writeResponse(HttpExchange exchange, String contentType, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.sendResponseHeaders(200, bytes.length);
        exchange.getResponseBody().write(bytes);
    }

    @FunctionalInterface
    private interface ExchangeHandler {
        void handle(HttpExchange exchange) throws IOException;
    }
}
