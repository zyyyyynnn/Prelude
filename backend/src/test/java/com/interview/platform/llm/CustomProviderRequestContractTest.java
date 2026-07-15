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
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class CustomProviderRequestContractTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void chatCompletionsUsesOfficialPayloadShape() throws Exception {
        AtomicReference<JsonNode> requestPayload = new AtomicReference<>();
        startServer("/v1/chat/completions", exchange -> {
            requestPayload.set(objectMapper.readTree(exchange.getRequestBody()));
            writeResponse(exchange, """
                {"choices":[{"message":{"content":"OK"}}],"usage":{"total_tokens":8}}
                """);
        });
        OpenAiChatCompletionsProvider provider = new OpenAiChatCompletionsProvider(
            objectMapper, mock(LlmMetricsTracker.class));

        String result = provider.chat(new LlmProvider.LlmInvocation(
            endpoint("/v1/chat/completions"), "gpt-test", "sk-test", messages(), 1024,
            Map.of(
                "thinking_depth", "high",
                "response_format", Map.of("type", "json_object")
            )
        ));

        assertThat(result).isEqualTo("OK");
        assertThat(requestPayload.get().path("messages").isArray()).isTrue();
        assertThat(requestPayload.get().path("max_tokens").asInt()).isEqualTo(1024);
        assertThat(requestPayload.get().path("reasoning_effort").asText()).isEqualTo("high");
        assertThat(requestPayload.get().at("/response_format/type").asText()).isEqualTo("json_object");
        assertThat(requestPayload.get().has("thinking_depth")).isFalse();
    }

    @Test
    void anthropicMessagesUsesHeadersAndSeparatesSystemPrompt() throws Exception {
        AtomicReference<JsonNode> requestPayload = new AtomicReference<>();
        AtomicReference<String> apiKey = new AtomicReference<>();
        AtomicReference<String> version = new AtomicReference<>();
        startServer("/v1/messages", exchange -> {
            requestPayload.set(objectMapper.readTree(exchange.getRequestBody()));
            apiKey.set(exchange.getRequestHeaders().getFirst("x-api-key"));
            version.set(exchange.getRequestHeaders().getFirst("anthropic-version"));
            writeResponse(exchange, """
                {"content":[{"type":"text","text":"OK"}],
                 "usage":{"input_tokens":4,"output_tokens":2}}
                """);
        });
        AnthropicProvider provider = new AnthropicProvider(objectMapper, mock(LlmMetricsTracker.class));

        String result = provider.chat(new LlmProvider.LlmInvocation(
            endpoint("/v1/messages"), "claude-test", "sk-ant", messages(), 2048,
            Map.of("response_format", Map.of("type", "json_object"))
        ));

        assertThat(result).isEqualTo("OK");
        assertThat(apiKey.get()).isEqualTo("sk-ant");
        assertThat(version.get()).isEqualTo("2023-06-01");
        assertThat(requestPayload.get().path("system").asText()).isEqualTo("system prompt");
        assertThat(requestPayload.get().path("messages")).hasSize(1);
        assertThat(requestPayload.get().path("max_tokens").asInt()).isEqualTo(2048);
        assertThat(requestPayload.get().has("response_format")).isFalse();
    }

    private List<Map<String, String>> messages() {
        return List.of(
            Map.of("role", "system", "content", "system prompt"),
            Map.of("role", "user", "content", "hello")
        );
    }

    private void startServer(String path, ExchangeHandler handler) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext(path, exchange -> {
            try {
                handler.handle(exchange);
            } finally {
                exchange.close();
            }
        });
        server.start();
    }

    private String endpoint(String path) {
        return "http://127.0.0.1:" + server.getAddress().getPort() + path;
    }

    private void writeResponse(HttpExchange exchange, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, bytes.length);
        exchange.getResponseBody().write(bytes);
    }

    @FunctionalInterface
    private interface ExchangeHandler {
        void handle(HttpExchange exchange) throws IOException;
    }
}
