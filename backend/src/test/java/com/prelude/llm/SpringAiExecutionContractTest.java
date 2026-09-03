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
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.retry.TransientAiException;
import org.springframework.context.ApplicationEventPublisher;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

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
        OpenAiChatOptions deepseekPlain = (OpenAiChatOptions) factory.requestOptions(
            deepseek, LlmPort.ResponseMode.PLAIN_TEXT);
        OpenAiChatOptions deepseekObject = (OpenAiChatOptions) factory.requestOptions(
            deepseek, LlmPort.ResponseMode.JSON_OBJECT);
        OpenAiChatOptions deepseekArray = (OpenAiChatOptions) factory.requestOptions(
            deepseek, LlmPort.ResponseMode.JSON_ARRAY);
        assertThat(deepseekPlain.getModel()).isEqualTo("deepseek-v4-pro");
        assertThat(deepseekPlain.getReasoningEffort()).isEqualTo("high");
        assertThat(deepseekPlain.getMaxTokens()).isEqualTo(4096);
        assertThat(deepseekPlain.getResponseFormat()).isNull();
        assertThat(deepseekObject.getResponseFormat().getType())
            .isEqualTo(OpenAiChatModel.ResponseFormat.Type.JSON_OBJECT);
        assertThat(deepseekArray.getResponseFormat()).isNull();
        assertThat(factory.chatModel(deepseek, "sk-test"))
            .isInstanceOf(OpenAiChatModel.class);

        ModelExecutionSnapshot chatCompletions = snapshot(
            "openai-chat-completions", "account-model", "AUTO", "https://example.com/v1");
        OpenAiChatOptions customPlain = (OpenAiChatOptions) factory.requestOptions(
            chatCompletions, LlmPort.ResponseMode.PLAIN_TEXT);
        OpenAiChatOptions customObject = (OpenAiChatOptions) factory.requestOptions(
            chatCompletions, LlmPort.ResponseMode.JSON_OBJECT);
        OpenAiChatOptions customArray = (OpenAiChatOptions) factory.requestOptions(
            chatCompletions, LlmPort.ResponseMode.JSON_ARRAY);
        assertThat(customPlain.getModel()).isEqualTo("account-model");
        assertThat(customPlain.getResponseFormat()).isNull();
        assertThat(customObject.getResponseFormat()).isNull();
        assertThat(customArray.getResponseFormat()).isNull();
        OpenAiChatOptions customHigh = (OpenAiChatOptions) factory.requestOptions(
            snapshot("openai-chat-completions", "account-model", "HIGH", "https://example.com/v1"),
            LlmPort.ResponseMode.PLAIN_TEXT);
        assertThat(customHigh.getReasoningEffort()).isEqualTo("high");
        OpenAiChatOptions customExtraHigh = (OpenAiChatOptions) factory.requestOptions(
            snapshot("openai-chat-completions", "account-model", "XHIGH", "https://example.com/v1"),
            LlmPort.ResponseMode.PLAIN_TEXT);
        OpenAiChatOptions customMax = (OpenAiChatOptions) factory.requestOptions(
            snapshot("openai-chat-completions", "account-model", "MAX", "https://example.com/v1"),
            LlmPort.ResponseMode.PLAIN_TEXT);
        assertThat(customExtraHigh.getReasoningEffort()).isEqualTo("xhigh");
        assertThat(customMax.getReasoningEffort()).isEqualTo("max");
        assertThat(customMax.getMaxTokens()).isEqualTo(4096);

        ChatOptions responses = factory.requestOptions(
            snapshot("openai-responses", "discovered-responses-model", "AUTO", "https://example.com/v1"),
            LlmPort.ResponseMode.JSON_OBJECT);
        AnthropicChatOptions anthropic = (AnthropicChatOptions) factory.requestOptions(
            snapshot("anthropic-messages", "discovered-anthropic-model", "AUTO", "https://example.com"),
            LlmPort.ResponseMode.JSON_ARRAY);
        assertThat(responses.getModel()).isEqualTo("discovered-responses-model");
        assertThat(anthropic.getModel()).isEqualTo("discovered-anthropic-model");
        assertThat(anthropic.getMaxTokens()).isEqualTo(4096);
        assertThat(anthropic.getThinking()).isNull();
        AnthropicChatOptions anthropicExtraHigh = (AnthropicChatOptions) factory.requestOptions(
            snapshot("anthropic-messages", "discovered-anthropic-model", "XHIGH", "https://example.com"),
            LlmPort.ResponseMode.PLAIN_TEXT);
        AnthropicChatOptions anthropicMax = (AnthropicChatOptions) factory.requestOptions(
            snapshot("anthropic-messages", "discovered-anthropic-model", "MAX", "https://example.com"),
            LlmPort.ResponseMode.PLAIN_TEXT);
        assertThat(anthropicExtraHigh.getOutputConfig().effort().orElseThrow().asString()).isEqualTo("xhigh");
        assertThat(anthropicMax.getOutputConfig().effort().orElseThrow().asString()).isEqualTo("max");
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
    void deepSeekBuiltInUsesExactAutoLowHighMaxReasoningOnTheRealWireContract() throws Exception {
        AtomicReference<String> requestPath = new AtomicReference<>();
        List<String> requestBodies = new ArrayList<>();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            requestPath.set(exchange.getRequestURI().getPath());
            requestBodies.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            respond(exchange, 200, """
                {"id":"deepseek-test","object":"chat.completion","created":1,"model":"deepseek-v4-pro",
                 "choices":[{"index":0,"message":{"role":"assistant","content":"deepseek-ok"},"finish_reason":"stop"}],
                 "usage":{"prompt_tokens":1,"completion_tokens":2,"total_tokens":3}}
                """);
        });
        server.start();

        ModelCapabilityCatalog catalog = new ModelCapabilityCatalog();
        CustomLlmEgressPolicy policy = new CustomLlmEgressPolicy(
            true, true, Set.of(server.getAddress().getPort()), Dns.SYSTEM);
        SpringAiModelFactory factory = new SpringAiModelFactory(
            "sk-system",
            "http://127.0.0.1:" + server.getAddress().getPort(),
            policy,
            new EgressHttpClientFactory(policy),
            catalog,
            new tools.jackson.databind.ObjectMapper()
        );
        for (String level : List.of("AUTO", "LOW", "HIGH", "MAX")) {
            ModelExecutionSnapshot snapshot = snapshot("deepseek", "deepseek-v4-pro", level, null);
            ChatModel model = factory.chatModel(snapshot, "sk-account");
            model.call(new Prompt(
                List.of(new org.springframework.ai.chat.messages.UserMessage("hello")),
                factory.requestOptions(snapshot, LlmPort.ResponseMode.JSON_OBJECT)));
        }

        assertThat(requestPath.get()).isEqualTo("/chat/completions");
        assertThat(requestBodies).hasSize(4);
        assertThat(requestBodies.get(0))
            .contains("\"model\":\"deepseek-v4-pro\"")
            .contains("\"max_tokens\":4096")
            .doesNotContain("reasoning_effort");
        assertThat(requestBodies.get(1))
            .contains("\"reasoning_effort\":\"low\"");
        assertThat(requestBodies.get(2))
            .contains("\"model\":\"deepseek-v4-pro\"")
            .contains("\"reasoning_effort\":\"high\"")
            .contains("\"max_tokens\":4096")
            .contains("\"response_format\":{\"type\":\"json_object\"}");
        assertThat(requestBodies.get(3))
            .contains("\"reasoning_effort\":\"max\"");
    }

    @Test
    void deepSeekRejectsDomainValidButAliasedMediumAndExtraHighLevels() {
        for (String level : List.of("MEDIUM", "XHIGH")) {
            ModelExecutionService service = fakeService(
                successfulCallModel("must-not-run"),
                snapshot("deepseek", "deepseek-v4-pro", level, null),
                1);

            assertThatThrownBy(() -> service.complete(request(1L, LlmPort.ResponseMode.PLAIN_TEXT)))
                .isInstanceOf(BusinessException.class)
                .hasMessage("所选模型不支持该思考深度");
        }
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
    void customChatCompletionsCanRequestSemanticJsonWithoutNativeStructuredOutput() throws Exception {
        AtomicInteger requests = new AtomicInteger();
        AtomicReference<String> requestBody = new AtomicReference<>();
        startOpenAiStub(exchange -> {
            requests.incrementAndGet();
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            respond(exchange, 200, completionJson("stub-model", "{\"ok\":true}"));
        });

        ModelExecutionService service = realCustomEndpointService(server.getAddress().getPort(), 3);
        LlmPort.CompletionResult result = service.complete(request(1L, LlmPort.ResponseMode.JSON_OBJECT));

        assertThat(result.content()).isEqualTo("{\"ok\":true}");
        assertThat(requests).hasValue(1);
        assertThat(requestBody.get()).doesNotContain("response_format");
    }

    @Test
    void openAiResponsesProtocolUsesTheDiscoveredModelWithoutSubstitution() throws Exception {
        AtomicReference<String> requestBody = new AtomicReference<>();
        startProtocolStub("/v1/responses", exchange -> {
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            respond(exchange, 200, """
                {"id":"resp-1","output_text":"responses-ok","usage":{"input_tokens":1,"output_tokens":2,"total_tokens":3}}
                """);
        });

        ModelExecutionService service = realCustomProtocolService(
            "openai-responses", "account-discovered-model", server.getAddress().getPort(), 1);
        LlmPort.CompletionResult result = service.complete(request(1L, LlmPort.ResponseMode.JSON_OBJECT));

        assertThat(result.content()).isEqualTo("responses-ok");
        assertThat(requestBody.get())
            .contains("\"model\":\"account-discovered-model\"")
            .contains("\"max_output_tokens\":4096");
    }

    @Test
    void openAiResponsesUsesEveryFrozenExplicitReasoningLevelOnTheWire() throws Exception {
        List<String> requestBodies = new ArrayList<>();
        startProtocolStub("/v1/responses", exchange -> {
            requestBodies.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            respond(exchange, 200, """
                {"id":"resp-1","output_text":"responses-ok","usage":{"input_tokens":1,"output_tokens":2,"total_tokens":3}}
                """);
        });

        for (String level : List.of("LOW", "MEDIUM", "HIGH", "XHIGH", "MAX")) {
            ModelExecutionService service = realCustomProtocolService(
                "openai-responses", "account-discovered-model", server.getAddress().getPort(), 1, level);
            service.complete(request(1L, LlmPort.ResponseMode.PLAIN_TEXT));
        }

        assertThat(requestBodies).hasSize(5);
        assertThat(requestBodies.get(0)).contains("\"reasoning\":{\"effort\":\"low\"}");
        assertThat(requestBodies.get(1)).contains("\"reasoning\":{\"effort\":\"medium\"}");
        assertThat(requestBodies.get(2)).contains("\"reasoning\":{\"effort\":\"high\"}");
        assertThat(requestBodies.get(3)).contains("\"reasoning\":{\"effort\":\"xhigh\"}");
        assertThat(requestBodies.get(4)).contains("\"reasoning\":{\"effort\":\"max\"}");
    }

    @Test
    void anthropicMessagesProtocolUsesItsOwnWireContractAndDiscoveredModel() throws Exception {
        AtomicReference<String> requestBody = new AtomicReference<>();
        AtomicReference<String> observedKey = new AtomicReference<>();
        startProtocolStub("/v1/messages", exchange -> {
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            observedKey.set(exchange.getRequestHeaders().getFirst("x-api-key"));
            respond(exchange, 200, """
                {"id":"msg-1","type":"message","role":"assistant","model":"account-discovered-model",
                 "content":[{"type":"text","text":"anthropic-ok"}],
                 "stop_reason":"end_turn","stop_sequence":null,
                 "usage":{"input_tokens":1,"output_tokens":2}}
                """);
        });

        ModelExecutionService service = realCustomProtocolService(
            "anthropic-messages", "account-discovered-model", server.getAddress().getPort(), 1);
        LlmPort.CompletionResult result = service.complete(request(1L, LlmPort.ResponseMode.JSON_ARRAY));

        assertThat(result.content()).isEqualTo("anthropic-ok");
        assertThat(observedKey.get()).isEqualTo("sk-test");
        assertThat(requestBody.get()).contains("\"model\":\"account-discovered-model\"");
    }

    @Test
    void anthropicMessagesUsesTheFrozenEffortLevelOnTheWire() throws Exception {
        AtomicReference<String> requestBody = new AtomicReference<>();
        startProtocolStub("/v1/messages", exchange -> {
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            respond(exchange, 200, """
                {"id":"msg-1","type":"message","role":"assistant","model":"account-discovered-model",
                 "content":[
                   {"type":"thinking","thinking":"private reasoning","signature":"sig"},
                   {"type":"text","text":"anthropic-ok"}
                 ],
                 "stop_reason":"end_turn","stop_sequence":null,
                 "usage":{"input_tokens":1,"output_tokens":2}}
                """);
        });

        ModelExecutionService service = realCustomProtocolService(
            "anthropic-messages", "account-discovered-model", server.getAddress().getPort(), 1, "MEDIUM");
        LlmPort.CompletionResult result = service.complete(request(1L, LlmPort.ResponseMode.PLAIN_TEXT));

        assertThat(result.content()).isEqualTo("anthropic-ok");
        assertThat(requestBody.get()).contains("\"thinking\":{\"type\":\"adaptive\"}");
        assertThat(requestBody.get()).contains("\"output_config\":{\"effort\":\"medium\"}");
        assertThat(requestBody.get()).contains("\"max_tokens\":4096");
    }

    @Test
    void anthropicStreamingHidesThinkingDeltasAndEmitsFinalText() throws Exception {
        startProtocolStub("/v1/messages", exchange -> {
            assertThat(exchange.getRequestURI().getPath()).isEqualTo("/v1/messages");
            String stream = """
                event: message_start
                data: {"type":"message_start","message":{"id":"msg-stream","type":"message","role":"assistant","model":"account-discovered-model","content":[],"stop_reason":null,"stop_sequence":null,"usage":{"input_tokens":2,"output_tokens":0}}}

                event: content_block_start
                data: {"type":"content_block_start","index":0,"content_block":{"type":"thinking","thinking":"","signature":""}}

                event: content_block_delta
                data: {"type":"content_block_delta","index":0,"delta":{"type":"thinking_delta","thinking":"private reasoning"}}

                event: content_block_delta
                data: {"type":"content_block_delta","index":0,"delta":{"type":"signature_delta","signature":"sig"}}

                event: content_block_stop
                data: {"type":"content_block_stop","index":0}

                event: content_block_start
                data: {"type":"content_block_start","index":1,"content_block":{"type":"text","text":""}}

                event: content_block_delta
                data: {"type":"content_block_delta","index":1,"delta":{"type":"text_delta","text":"anthropic-stream-ok"}}

                event: content_block_stop
                data: {"type":"content_block_stop","index":1}

                event: message_delta
                data: {"type":"message_delta","delta":{"stop_reason":"end_turn","stop_sequence":null},"usage":{"output_tokens":3}}

                event: message_stop
                data: {"type":"message_stop"}

                """;
            byte[] bytes = stream.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
            exchange.sendResponseHeaders(200, bytes.length);
            try (var output = exchange.getResponseBody()) {
                output.write(bytes);
            }
        });

        ModelExecutionService service = realCustomProtocolService(
            "anthropic-messages", "account-discovered-model", server.getAddress().getPort(), 1, "HIGH");
        List<String> deltas = new ArrayList<>();

        service.stream(request(1L, LlmPort.ResponseMode.PLAIN_TEXT), sink(deltas));

        assertThat(String.join("", deltas)).isEqualTo("anthropic-stream-ok");
        assertThat(deltas).noneMatch(delta -> delta.contains("private reasoning"));
    }

    @Test
    void streamingToolsAreRejectedInsteadOfSilentlyDiscarded() {
        AtomicInteger subscriptions = new AtomicInteger();
        ChatModel model = new ChatModel() {
            @Override
            public ChatResponse call(Prompt prompt) {
                throw new UnsupportedOperationException();
            }

            @Override
            public Flux<ChatResponse> stream(Prompt prompt) {
                subscriptions.incrementAndGet();
                return Flux.just(response("unexpected"));
            }
        };
        ModelExecutionService service = fakeService(
            model, snapshot("deepseek", "deepseek-v4-pro", "AUTO", null), 1);
        LlmPort.ToolBinding tool = new LlmPort.ToolBinding(
            "noop", "noop", "{\"type\":\"object\"}", ignored -> "ok");
        LlmPort.ModelExecutionRequest streamingToolRequest = new LlmPort.ModelExecutionRequest(
            1L,
            "test",
            "test.prompt",
            LlmPort.ResponseMode.PLAIN_TEXT,
            List.of(new LlmPort.Message("user", "hello")),
            List.of(),
            List.of(tool)
        );

        assertThatThrownBy(() -> service.stream(streamingToolRequest, sink(new ArrayList<>())))
            .isInstanceOf(BusinessException.class)
            .hasMessage("当前不支持流式工具调用");
        assertThat(subscriptions).hasValue(0);
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
        ModelExecutionService service = fakeService(
            model, snapshot("deepseek", "deepseek-v4-pro", "AUTO", null), 3);
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
        ModelExecutionSnapshot snapshot = snapshot("deepseek", "deepseek-v4-pro", "AUTO", null);
        snapshot.setFallbackModelsJson("[]");
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
        frozen.setEffectiveParametersJson("{\"maxOutputTokens\":8192}");
        frozen.setFallbackModelsJson("[\"deepseek-v4-flash\"]");
        when(snapshotService.require(1L)).thenReturn(frozen);
        when(profileService.resolveApiKey(anyLong(), nullable(Long.class))).thenReturn(null);
        List<String> executionParameters = new ArrayList<>();
        when(factory.requestOptions(any(), any())).thenAnswer(invocation -> {
            ModelExecutionSnapshot effective = invocation.getArgument(0);
            executionParameters.add(effective.getEffectiveParametersJson());
            return OpenAiChatOptions.builder().model(effective.getModel()).maxTokens(8192).build();
        });
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
            factory, snapshotService, profileService, catalog, new LlmTransportRetry(1),
            mock(ApplicationEventPublisher.class));

        LlmPort.CompletionResult result = service.complete(request(1L, LlmPort.ResponseMode.PLAIN_TEXT));

        assertThat(result.content()).isEqualTo("fallback-ok");
        assertThat(executedModels).containsExactly("deepseek-v4-pro", "deepseek-v4-flash");
        assertThat(executionParameters)
            .containsExactly("{\"maxOutputTokens\":8192}", "{\"maxOutputTokens\":8192}");
    }

    private ModelExecutionService realCustomEndpointService(int port, int attempts) {
        return realCustomProtocolService("openai-chat-completions", "stub-model", port, attempts);
    }

    private ModelExecutionService realCustomProtocolService(
        String provider,
        String model,
        int port,
        int attempts
    ) {
        return realCustomProtocolService(provider, model, port, attempts, "AUTO");
    }

    private ModelExecutionService realCustomProtocolService(
        String provider,
        String model,
        int port,
        int attempts,
        String reasoning
    ) {
        String endpointRoot = "http://127.0.0.1:" + port
            + ("anthropic-messages".equals(provider) ? "" : "/v1");
        ModelExecutionSnapshot snapshot = snapshot(
            provider, model, reasoning, endpointRoot);
        ModelExecutionSnapshotService snapshotService = mock(ModelExecutionSnapshotService.class);
        ModelProfileService profileService = mock(ModelProfileService.class);
        when(snapshotService.require(1L)).thenReturn(snapshot);
        when(profileService.resolveApiKey(anyLong(), nullable(Long.class))).thenReturn("sk-test");
        ModelCapabilityCatalog catalog = new ModelCapabilityCatalog();
        CustomLlmEgressPolicy policy = new CustomLlmEgressPolicy(
            true, true, Set.of(port), Dns.SYSTEM);
        SpringAiModelFactory factory = factoryFor(policy);
        return new ModelExecutionService(
            factory, snapshotService, profileService, catalog, new LlmTransportRetry(attempts),
            mock(ApplicationEventPublisher.class));
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
            factory, snapshotService, profileService, new ModelCapabilityCatalog(), new LlmTransportRetry(attempts),
            mock(ApplicationEventPublisher.class));
    }

    private SpringAiModelFactory factoryFor(CustomLlmEgressPolicy policy) {
        ModelCapabilityCatalog catalog = new ModelCapabilityCatalog();
        return new SpringAiModelFactory(
            "", "https://api.deepseek.com",
            policy, new EgressHttpClientFactory(policy), catalog, new tools.jackson.databind.ObjectMapper());
    }

    private void startOpenAiStub(com.sun.net.httpserver.HttpHandler handler) throws IOException {
        startProtocolStub("/v1/chat/completions", handler);
    }

    private void startProtocolStub(String path, com.sun.net.httpserver.HttpHandler handler) throws IOException {
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

    private String completionJson(String model, String content) {
        try {
            String encodedContent = new tools.jackson.databind.ObjectMapper().writeValueAsString(content);
            return """
                {"id":"chatcmpl-test","object":"chat.completion","created":1,"model":"%s",
                 "choices":[{"index":0,"message":{"role":"assistant","content":%s},"finish_reason":"stop"}],
                 "usage":{"prompt_tokens":1,"completion_tokens":1,"total_tokens":2}}
                """.formatted(model, encodedContent);
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
    }

    private ModelExecutionSnapshot snapshot(String provider, String model, String reasoning, String endpoint) {
        ModelExecutionSnapshot snapshot = new ModelExecutionSnapshot();
        snapshot.setId(1L);
        snapshot.setAccountId(7L);
        snapshot.setProfileId(9L);
        snapshot.setProvider(provider);
        snapshot.setModel(model);
        snapshot.setReasoningLevel(reasoning);
        snapshot.setEffectiveParametersJson("{\"maxOutputTokens\":4096}");
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
        return deltas::add;
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
