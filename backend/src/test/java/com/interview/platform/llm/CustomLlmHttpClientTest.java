package com.interview.platform.llm;

import com.interview.shared.api.LlmServerException;
import com.sun.net.httpserver.HttpServer;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.BufferedSource;
import okio.Okio;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.StringReader;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CustomLlmHttpClientTest {

    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) server.stop(0);
    }

    @Test
    void neverFollowsRedirects() throws Exception {
        AtomicInteger redirectedHits = new AtomicInteger();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/start", exchange -> {
            exchange.getResponseHeaders().set("Location", "/redirected");
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });
        server.createContext("/redirected", exchange -> {
            redirectedHits.incrementAndGet();
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        server.start();
        CustomLlmHttpClient client = CustomLlmTestClients.localClient(server.getAddress().getPort());
        Request request = new Request.Builder().url(endpoint("/start")).build();

        try (Response response = client.execute(request, Duration.ofSeconds(2))) {
            assertThat(response.code()).isEqualTo(302);
        }
        assertThat(redirectedHits).hasValue(0);
    }

    @Test
    void rejectsOversizedBodiesAndStreamEvents() {
        CustomLlmHttpClient client = CustomLlmTestClients.localClient(443);
        byte[] oversized = new byte[CustomLlmHttpClient.MAX_RESPONSE_BYTES + 1];
        ResponseBody body = ResponseBody.create(oversized, MediaType.get("application/json"));

        assertThatThrownBy(() -> client.readBody(body))
            .isInstanceOf(LlmServerException.class)
            .hasMessageContaining("安全上限");

        String oversizedLine = "x".repeat(CustomLlmHttpClient.MAX_STREAM_LINE_CHARS + 1);
        assertThatThrownBy(() -> client.readStreamLine(new BufferedReader(new StringReader(oversizedLine))))
            .isInstanceOf(LlmServerException.class)
            .hasMessageContaining("安全上限");
    }

    @Test
    void rejectsStreamWhenSmallEventsExceedTheCumulativeByteLimit() {
        CustomLlmHttpClient client = CustomLlmTestClients.localClient(443);
        byte[] payload = ("data: x\n\n".repeat(CustomLlmHttpClient.MAX_RESPONSE_BYTES / 8 + 1))
            .getBytes(java.nio.charset.StandardCharsets.UTF_8);
        ResponseBody body = unknownLengthBody(payload);

        assertThatThrownBy(() -> {
            try (BufferedReader reader = client.openStreamReader(body)) {
                while (client.readStreamLine(reader) != null) {
                    // Consume the complete chunked response.
                }
            }
        })
            .isInstanceOf(LlmServerException.class)
            .hasMessageContaining("安全上限");
    }

    @Test
    void revalidatesDnsAnswersWhenOpeningTheConnection() throws Exception {
        AtomicInteger lookups = new AtomicInteger();
        CustomLlmEgressPolicy policy = new CustomLlmEgressPolicy(
            true,
            false,
            Set.of(80),
            hostname -> lookups.incrementAndGet() == 1
                ? List.of(InetAddress.getByName("93.184.216.34"))
                : List.of(InetAddress.getByName("127.0.0.1"))
        );
        policy.validateConfiguredEndpoint("http://models.example/v1");
        CustomLlmHttpClient client = new CustomLlmHttpClient(policy);
        Request request = new Request.Builder().url("http://models.example/v1").build();

        assertThatThrownBy(() -> client.execute(request, Duration.ofSeconds(1)))
            .isInstanceOf(UnknownHostException.class)
            .hasMessageContaining("Blocked non-public address");
        assertThat(lookups).hasValue(2);
    }

    private String endpoint(String path) {
        return "http://127.0.0.1:" + server.getAddress().getPort() + path;
    }

    private ResponseBody unknownLengthBody(byte[] payload) {
        return new ResponseBody() {
            @Override public MediaType contentType() { return MediaType.get("text/event-stream"); }
            @Override public long contentLength() { return -1; }
            @Override public BufferedSource source() {
                return Okio.buffer(Okio.source(new ByteArrayInputStream(payload)));
            }
        };
    }
}
