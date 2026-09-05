package com.prelude.llm;

import com.prelude.llm.api.LlmPort;
import com.prelude.llm.api.ModelCapabilityResponse;
import com.prelude.llm.persistence.ModelProfileMapper;
import com.prelude.llm.persistence.ProviderCredentialMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import okhttp3.Dns;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class CustomModelDiscoveryTest {

    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void openAiResponsesStripsOnlyItsEndpointSuffixBeforeModelDiscovery() throws Exception {
        DiscoveryObservation observation = discover(
            CustomLlmProtocol.OPENAI_RESPONSES,
            "/v1/responses/"
        );

        assertThat(observation.path()).isEqualTo("/v1/models");
        assertThat(observation.authorization()).isEqualTo("Bearer account-key");
        assertThat(observation.anthropicKey()).isNull();
    }

    @Test
    void openAiChatCompletionsStripsTheWholeChatCompletionsSuffix() throws Exception {
        DiscoveryObservation observation = discover(
            CustomLlmProtocol.OPENAI_CHAT_COMPLETIONS,
            "/v1/chat/completions/"
        );

        assertThat(observation.path()).isEqualTo("/v1/models");
        assertThat(observation.authorization()).isEqualTo("Bearer account-key");
        assertThat(observation.anthropicKey()).isNull();
    }

    @Test
    void anthropicMessagesUsesModelsEndpointAndAnthropicAuthentication() throws Exception {
        DiscoveryObservation observation = discover(
            CustomLlmProtocol.ANTHROPIC_MESSAGES,
            "/v1/messages/"
        );

        assertThat(observation.path()).isEqualTo("/v1/models");
        assertThat(observation.authorization()).isNull();
        assertThat(observation.anthropicKey()).isEqualTo("account-key");
        assertThat(observation.anthropicVersion()).isEqualTo("2023-06-01");
    }

    private DiscoveryObservation discover(CustomLlmProtocol protocol, String configuredPath) throws Exception {
        AtomicReference<DiscoveryObservation> observed = new AtomicReference<>();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/models", exchange -> {
            observed.set(observation(exchange));
            respond(exchange, 200, "{\"data\":[{\"id\":\"account-discovered-model\"}]}");
        });
        server.start();

        int port = server.getAddress().getPort();
        CustomLlmEgressPolicy policy = new CustomLlmEgressPolicy(
            true, true, Set.of(port), Dns.SYSTEM);
        ModelCapabilityCatalog catalog = new ModelCapabilityCatalog();
        tools.jackson.databind.ObjectMapper objectMapper = new tools.jackson.databind.ObjectMapper();
        EgressHttpClientFactory httpClientFactory = new EgressHttpClientFactory(policy);
        CustomModelCapabilityDiscovery capabilityDiscovery = new CustomModelCapabilityDiscovery(
            catalog, policy, httpClientFactory, objectMapper);
        ModelProfileService service = new ModelProfileService(
            mock(ProviderCredentialMapper.class),
            mock(ModelProfileMapper.class),
            mock(ProviderSecretCipher.class),
            catalog,
            new ReasoningLevels(),
            capabilityDiscovery,
            policy,
            httpClientFactory,
            new ModelCapabilityJson(objectMapper),
            objectMapper,
            mock(org.springframework.transaction.support.TransactionTemplate.class)
        );
        String configuredUrl = "http://127.0.0.1:" + port + configuredPath;

        LlmPort.DiscoveredModelsView result = service.discoverCustomModels(
            7L,
            new LlmPort.DiscoverModelsCommand(protocol.providerKey(), configuredUrl, "account-key")
        );

        String expectedRoot = "http://127.0.0.1:" + port
            + (protocol == CustomLlmProtocol.ANTHROPIC_MESSAGES ? "" : "/v1");
        assertThat(result.baseUrl()).isEqualTo(expectedRoot);
        assertThat(result.models()).singleElement().satisfies(model -> {
            assertThat(model.provider()).isEqualTo(protocol.providerKey());
            assertThat(model.model()).isEqualTo("account-discovered-model");
            assertThat(model.streaming()).isTrue();
            assertThat(model.structuredOutput()).isFalse();
            assertThat(model.toolCalling()).isFalse();
            assertThat(model.reasoning()).isFalse();
            assertThat(model.supportedReasoningLevels())
                .containsExactly(ModelCapabilityResponse.ReasoningLevel.AUTO);
        });
        return observed.get();
    }

    private DiscoveryObservation observation(HttpExchange exchange) {
        return new DiscoveryObservation(
            exchange.getRequestURI().getPath(),
            exchange.getRequestHeaders().getFirst("Authorization"),
            exchange.getRequestHeaders().getFirst("x-api-key"),
            exchange.getRequestHeaders().getFirst("anthropic-version")
        );
    }

    private void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (var output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }

    private record DiscoveryObservation(
        String path,
        String authorization,
        String anthropicKey,
        String anthropicVersion
    ) {
    }
}
