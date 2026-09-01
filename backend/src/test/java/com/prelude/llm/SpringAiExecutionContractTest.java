package com.prelude.llm;

import com.prelude.BusinessException;
import com.prelude.LlmServerException;
import com.prelude.llm.api.LlmPort;
import com.prelude.llm.persistence.ModelExecutionSnapshot;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import okhttp3.Dns;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.deepseek.DeepSeekChatOptions;
import org.springframework.ai.deepseek.api.ResponseFormat;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.retry.TransientAiException;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SpringAiExecutionContractTest {

    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void responseModeUsesProviderNativeOptionsAndBindsTheExactFrozenModel() {
        SpringAiModelFactory factory = factoryFor(new CustomLlmEgressPolicy(
            false, false, Set.of(443), Dns.SYSTEM));

        ModelExecutionSnapshot deepseek = snapshot("deepseek", "deepseek-v4-pro", "HIGH", null);
        DeepSeekChatOptions deepseekPlain = (DeepSeekChatOptions) factory.requestOptions(
            deepseek, LlmPort.ResponseMode.PLAIN_TEXT);
        DeepSeekChatOptions deepseekJson = (DeepSeekChatOptions) factory.requestOptions(
            deepseek, LlmPort.ResponseMode.JSON);
        assertThat(deepseekPlain.getModel()).isEqualTo("deepseek-v4-pro");
        assertThat(deepseekPlain.getResponseFormat()).isNull();
        assertThat(deepseekJson.getResponseFormat().getType()).isEqualTo(ResponseFormat.Type.JSON_OBJECT);

        ModelExecutionSnapshot openAi = snapshot("openai", "gpt-5.4", "MEDIUM", null);
        OpenAiChatOptions openAiPlain = (OpenAiChatOptions) factory.requestOptions(
            openAi, LlmPort.ResponseMode.PLAIN_TEXT);
        OpenAiChatOptions openAiJson = (OpenAiChatOptions) factory.requestOptions(
            openAi, LlmPort.ResponseMode.JSON);
        assertThat(openAiPlain.getModel()).isEqualTo("gpt-5.4");
        assertThat(openAiPlain.getResponseFormat()).isNull();
        assertThat(openAiJson.getResponseFormat().getType())
            .isEqualTo(OpenAiChatModel.ResponseFormat.Type.JSON_OBJECT);

        ModelExecutionSnapshot anthropic = snapshot("anthropic", "claude-sonnet-4-6", "LOW", null);
        AnthropicChatOptions anthropicPlain = (AnthropicChatOptions) factory.requestOptions(
            anthropic, LlmPort.ResponseMode.PLAIN_TEXT);
        AnthropicChatOptions anthropicJson = (AnthropicChatOptions) factory.requestOptions(
            anthropic, LlmPort.ResponseMode.JSON);
        assertThat(anthropicPlain.getModel()).isEqualTo("claude-sonnet-4-6");
        assertThat(anthropicPlain.getOutputSchema()).isNull();
        assertThat(anthropicJson.getOutputSchema()).isEqualTo("{\"type\":\"object\"}");
    }

    @Test
    void unknownDeepSeekModelIsNeverSubstitutedWithAnotherWireModel() {
        SpringAiModelFactory factory = factoryFor(new CustomLlmEgressPolicy(
            false, false, Set.of(443), Dns.SYSTEM));
        ModelExecutionSnapshot unknown = snapshot("deepseek", "deepseek-not-real", "AUTO", null);

        assertThatThrownBy(() -> factory.chatModel(unknown, "sk-test"))
            .isInstanceOf(BusinessException.class)
            .hasMessage("当前接入方式不支持该模型");
    }

    @Test
    void customEndpointTransientFailuresProduceExactlyThreeActualHttpRequests() throws Exception {
        AtomicInteger requests = new AtomicInteger();
        startOpenAiStub(exchange -> {
            int attempt = requests.incrementAndGet();
            if (attempt < 3) {
                respond(exchange, 500, "{\"error\":{\"message\":\"temporary\",\"type\":\"server_error\"}}");
            } else {
                respond(exchange, 200, completionJson("stub-model", "ok"));
            }
        });

        ModelExecutionService service = realCustomEndpointService(server.getAddress().getPort(), 3);
        LlmPort.CompletionResult result = service.complete(request(1L, LlmPort.ResponseMode.PLAIN_TEXT));

        assertThat(result.content()).isEqualTo("ok");
        assertThat(requests).hasValue(3);
    }

    @Test
    void customEndpointNonTransientFailureIsNotRetried() throws Exception {
        AtomicInteger requests = new AtomicInteger();
        startOpenAiStub(exchange -> {
            requests.incrementAndGet();
            respond(exchange, 400,
                "{\"error\":{\"message\":\"bad request\",\"type\":\"invalid_request_error\"}}");
        });

        ModelExecutionService service = realCustomEndpointService(server.getAddress().getPort(), 3);

        assertThatThrownBy(() -> service.complete(request(1L, LlmPort.ResponseMode.PLAIN_TEXT)))
            .isInstanceOf(BusinessException.class);
        assertThat(requests).hasValue(1);
    }

    @Test
    void streamingRetriesOnlyBeforeTheFirstVisibleDelta() {
        AtomicInteger subscriptions = new AtomicInteger();
        ChatModel model = new ChatModel() {
            @Override
            public ChatResponse call(Prompt prompt) {
                throw new UnsupportedOperationException();
            }

            @Override
            public Flux<ChatResponse> stream(Prompt prompt) {
                return Flux.defer(() -> {
                    int attempt = subscriptions.incrementAndGet();
                    return attempt < 3
                        ? Flux.error(new TransientAiException("transient"))
                        : Flux.just(response("done"));
                });
            }
        };
        ModelExecutionService service = fakeService(model, snapshot("openai", "gpt-5.4", "AUTO", null), 3);
        List<String> deltas = new ArrayList<>();

        service.stream(request(1L, LlmPort.ResponseMode.PLAIN_TEXT), sink(deltas));

        assertThat(subscriptions).hasValue(3);
        assertThat(deltas).containsExactly("done");
    }

    @Test
    void streamingNeverRetriesOrFallsBackAfterTheFirstVisibleDelta() {
        AtomicInteger subscriptions = new AtomicInteger();
        ChatModel model = new ChatModel() {
            @Override
            public ChatResponse call(Prompt prompt) {
                throw new UnsupportedOperationException();
            }

            @Override
            public Flux<ChatResponse> stream(Prompt prompt) {
                return Flux.defer(() -> {
                    subscriptions.incrementAndGet();
                    return Flux.concat(
                        Flux.just(response("partial")),
                        Flux.error(new TransientAiException("lost after delta")));
                });
            }
        };
        ModelExecutionSnapshot snapshot = snapshot("openai", "gpt-5.4", "AUTO", null);
        snapshot.setFallbackModelsJson("[\"gpt-5.4\"]");
        ModelExecutionService service = fakeService(model, snapshot, 3);
        List<String> deltas = new ArrayList<>();

        assertThatThrownBy(() -> service.stream(request(1L, LlmPort.ResponseMode.PLAIN_TEXT), sink(deltas)))
            .isInstanceOf(LlmServerException.class);
        assertThat(subscriptions).hasValue(1);
        assertThat(deltas).containsExactly("partial");
    }

    @Test
    void fallbackRunsInFrozenOrderOnlyAfterPrimaryTransientFailure() {
        SpringAiModelFactory factory = mock(SpringAiModelFactory.class);
        ModelExecutionSnapshotService snapshotService = mock(ModelExecutionSnapshotService.class);
        ModelProfileService profileService = mock(ModelProfileService.class);
        ModelCapabilityCatalog catalog = new ModelCapabilityCatalog();
        ModelExecutionSnapshot frozen = snapshot("deepseek", "deepseek-v4-pro", "AUTO", null);
        frozen.setFallbackModelsJson("[\"deepseek-v4-flash\"]");
        when(snapshotService.require(1L)).thenReturn(frozen);
        when(profileService.resolveApiKey(anyLong(), nullable(Long.class))).thenReturn(null);
        when(factory.requestOptions(any(), any())).thenReturn(
            DeepSeekChatOptions.builder().model(org.springframework.ai.deepseek.api.DeepSeekApi.ChatModel.DEEPSEEK_V4_PRO).build());
        List<String> executedModels = new ArrayList<>();
        when(factory.chatModel(any(), nullable(String.class))).thenAnswer(invocation -> {
            ModelExecutionSnapshot effective = invocation.getArgument(0);
            executedModels.add(effective.getModel());
            if ("deepseek-v4-pro".equals(effective.getModel())) {
                return failingCallModel();
            }
            return successfulCallModel("fallback-ok");
        });
        ModelExecutionService service = new ModelExecutionService(
            factory, snapshotService, profileService, catalog, 1);

        LlmPort.CompletionResult result = service.complete(request(1L, LlmPort.ResponseMode.PLAIN_TEXT));

        assertThat(result.content()).isEqualTo("fallback-ok");
        assertThat(executedModels).containsExactly("deepseek-v4-pro", "deepseek-v4-flash");
    }

    private ModelExecutionService realCustomEndpointService(int port, int attempts) {
        ModelExecutionSnapshot snapshot = snapshot(
            "openai-compatible", "stub-model", "AUTO", "http://127.0.0.1:" + port + "/v1");
        ModelExecutionSnapshotService snapshotService = mock(ModelExecutionSnapshotService.class);
        ModelProfileService profileService = mock(ModelProfileService.class);
        when(snapshotService.require(1L)).thenReturn(snapshot);
        when(profileService.resolveApiKey(anyLong(), nullable(Long.class))).thenReturn("sk-test");
        ModelCapabilityCatalog catalog = new ModelCapabilityCatalog();
        CustomLlmEgressPolicy policy = new CustomLlmEgressPolicy(
            true, true, Set.of(port), Dns.SYSTEM);
        SpringAiModelFactory factory = factoryFor(policy);
        return new ModelExecutionService(factory, snapshotService, profileService, catalog, attempts);
    }

    private ModelExecutionService fakeService(ChatModel model, ModelExecutionSnapshot snapshot, int attempts) {
        SpringAiModelFactory factory = mock(SpringAiModelFactory.class);
        ModelExecutionSnapshotService snapshotService = mock(ModelExecutionSnapshotService.class);
        ModelProfileService profileService = mock(ModelProfileService.class);
        when(snapshotService.require(1L)).thenReturn(snapshot);
        when(profileService.resolveApiKey(anyLong(), nullable(Long.class))).thenReturn(null);
        when(factory.chatModel(any(), nullable(String.class))).thenReturn(model);
        when(factory.requestOptions(any(), any())).thenReturn(
            OpenAiChatOptions.builder().model(snapshot.getModel()).build());
        return new ModelExecutionService(
            factory, snapshotService, profileService, new ModelCapabilityCatalog(), attempts);
    }

    private SpringAiModelFactory factoryFor(CustomLlmEgressPolicy policy) {
        ModelCapabilityCatalog catalog = new ModelCapabilityCatalog();
        return new SpringAiModelFactory(
            "", "https://api.deepseek.com", "", "",
            policy, new EgressHttpClientFactory(policy), catalog);
    }

    private void startOpenAiStub(com.sun.net.httpserver.HttpHandler handler) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", handler);
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

    private String completionJson(String model, String content) {
        return """
            {"id":"chatcmpl-test","object":"chat.completion","created":1,"model":"%s",
             "choices":[{"index":0,"message":{"role":"assistant","content":"%s"},"finish_reason":"stop"}],
             "usage":{"prompt_tokens":1,"completion_tokens":1,"total_tokens":2}}
            """.formatted(model, content);
    }

    private ModelExecutionSnapshot snapshot(String provider, String model, String reasoning, String endpoint) {
        ModelExecutionSnapshot snapshot = new ModelExecutionSnapshot();
        snapshot.setId(1L);
        snapshot.setAccountId(7L);
        snapshot.setProfileId(9L);
        snapshot.setProvider(provider);
        snapshot.setModel(model);
        snapshot.setReasoningLevel(reasoning);
        snapshot.setEffectiveParametersJson("{}");
        snapshot.setCapabilityVersion(ModelCapabilityCatalog.CAPABILITY_VERSION);
        snapshot.setFallbackModelsJson("[]");
        snapshot.setCustomEndpointUrl(endpoint);
        return snapshot;
    }

    private LlmPort.ModelExecutionRequest request(long snapshotId, LlmPort.ResponseMode mode) {
        return new LlmPort.ModelExecutionRequest(
            snapshotId, "test", "test.prompt", mode,
            List.of(new LlmPort.Message("user", "hello")), List.of(), List.of());
    }

    private LlmPort.StreamSink sink(List<String> deltas) {
        return new LlmPort.StreamSink() {
            @Override
            public void onNext(String delta) {
                deltas.add(delta);
            }

            @Override
            public void onUsage(LlmPort.Usage usage) {
            }
        };
    }

    private ChatModel failingCallModel() {
        return prompt -> {
            throw new TransientAiException("transient");
        };
    }

    private ChatModel successfulCallModel(String content) {
        return prompt -> response(content);
    }

    private ChatResponse response(String content) {
        return new ChatResponse(List.of(new Generation(
            new org.springframework.ai.chat.messages.AssistantMessage(content))));
    }
}
