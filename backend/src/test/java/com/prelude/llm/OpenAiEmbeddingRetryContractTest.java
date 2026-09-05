package com.prelude.llm;

import com.prelude.BusinessException;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OpenAiEmbeddingRetryContractTest {

    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void transientEmbeddingFailuresUseExactlyThePreludeAttemptBudget() throws Exception {
        AtomicInteger requests = new AtomicInteger();
        start(exchange -> {
            int attempt = requests.incrementAndGet();
            if (attempt < 3) {
                respond(exchange, 500, "{\"error\":{\"message\":\"temporary\",\"type\":\"server_error\"}}");
                return;
            }
            respond(exchange, 200, embeddingJson());
        });
        OpenAiEmbeddingAdapter adapter = adapter(3);

        assertThat(adapter.embed("hello")).containsExactly(0.25f, 0.75f);
        assertThat(requests).hasValue(3);
    }

    @Test
    void nonTransientEmbeddingFailureIsAttemptedOnce() throws Exception {
        AtomicInteger requests = new AtomicInteger();
        start(exchange -> {
            requests.incrementAndGet();
            respond(exchange, 400,
                "{\"error\":{\"message\":\"bad request\",\"type\":\"invalid_request_error\"}}");
        });
        OpenAiEmbeddingAdapter adapter = adapter(3);

        assertThatThrownBy(() -> adapter.embed("hello")).isInstanceOf(BusinessException.class);
        assertThat(requests).hasValue(1);
    }

    private OpenAiEmbeddingAdapter adapter(int attempts) {
        return new OpenAiEmbeddingAdapter(
            "http://127.0.0.1:" + server.getAddress().getPort() + "/v1",
            "sk-test",
            "text-embedding-3-small",
            new LlmTransportRetry(attempts)
        );
    }

    private void start(com.sun.net.httpserver.HttpHandler handler) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/embeddings", handler);
        server.start();
    }

    private String embeddingJson() {
        return """
            {"object":"list","data":[{"object":"embedding","embedding":[0.25,0.75],"index":0}],
             "model":"text-embedding-3-small","usage":{"prompt_tokens":1,"total_tokens":1}}
            """;
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
