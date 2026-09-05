package com.prelude.llm;

import com.prelude.LlmServerException;
import com.prelude.llm.api.LlmPort;
import com.prelude.llm.api.LlmUsageRecorded;
import com.prelude.llm.persistence.ModelExecutionSnapshot;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import okhttp3.Dns;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.retry.TransientAiException;
import org.springframework.context.ApplicationEventPublisher;

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

class SpringAiToolCallingContractTest {

    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void continuationTransportRetryDoesNotReplayCommittedToolAndAggregatesUsage() {
        AtomicInteger modelCalls = new AtomicInteger();
        AtomicInteger toolCalls = new AtomicInteger();
        AtomicInteger usageEvents = new AtomicInteger();
        AtomicReference<LlmUsageRecorded> usageEvent = new AtomicReference<>();
        ChatModel model = prompt -> {
            int call = modelCalls.incrementAndGet();
            if (call == 1) {
                return toolRequest("call-1", "double_value", "{\"value\":21}", 2, 1);
            }
            assertThat(prompt.getInstructions())
                .anyMatch(message -> message instanceof ToolResponseMessage response
                    && response.getResponses().stream().anyMatch(tool -> "42".equals(tool.responseData())));
            if (call == 2) {
                throw new TransientAiException("continuation transport failed once");
            }
            return response("The tool returned 42.", 3, 2);
        };
        ApplicationEventPublisher events = eventPublisher(usageEvent, usageEvents);
        ModelExecutionService service = service(snapshot(), 2, events, effective -> model);

        LlmPort.CompletionResult result = service.complete(request(tool("double_value", arguments -> {
            toolCalls.incrementAndGet();
            return "42";
        })));

        assertThat(modelCalls).hasValue(3);
        assertThat(toolCalls).hasValue(1);
        assertThat(result.content()).isEqualTo("The tool returned 42.");
        assertThat(result.usage().inputTokens()).isEqualTo(5L);
        assertThat(result.usage().outputTokens()).isEqualTo(3L);
        assertThat(result.usage().totalTokens()).isEqualTo(8L);
        assertThat(usageEvents).hasValue(1);
        assertThat(usageEvent.get()).isNotNull();
        assertThat(usageEvent.get().inputTokens()).isEqualTo(5L);
        assertThat(usageEvent.get().outputTokens()).isEqualTo(3L);
        assertThat(usageEvent.get().estimatedCost()).isNull();
    }

    @Test
    void exhaustedContinuationNeverFallsBackAfterCommittedTool() {
        AtomicInteger primaryCalls = new AtomicInteger();
        AtomicInteger fallbackCalls = new AtomicInteger();
        AtomicInteger toolCalls = new AtomicInteger();
        AtomicInteger usageEvents = new AtomicInteger();
        AtomicReference<LlmUsageRecorded> usageEvent = new AtomicReference<>();
        ChatModel primary = prompt -> {
            int call = primaryCalls.incrementAndGet();
            if (call == 1) {
                return toolRequest("call-1", "commit_side_effect", "{}", 1, 1);
            }
            throw new TransientAiException("continuation unavailable");
        };
        ChatModel fallback = prompt -> {
            fallbackCalls.incrementAndGet();
            return response("must-not-run", 1, 1);
        };
        ModelExecutionSnapshot snapshot = snapshot();
        snapshot.setFallbackCapabilitiesJson(fallbackCapabilities());
        ModelExecutionService service = service(
            snapshot, 2, eventPublisher(usageEvent, usageEvents),
            effective -> "deepseek-v4-pro".equals(effective.getModel()) ? primary : fallback);

        assertThatThrownBy(() -> service.complete(request(tool("commit_side_effect", arguments -> {
            toolCalls.incrementAndGet();
            return "committed";
        }))))
            .isInstanceOf(LlmServerException.class);

        assertThat(primaryCalls).hasValue(3);
        assertThat(fallbackCalls).hasValue(0);
        assertThat(toolCalls).hasValue(1);
        assertThat(usageEvents).hasValue(1);
        assertThat(usageEvent.get().model()).isEqualTo("deepseek-v4-pro");
        assertThat(usageEvent.get().inputTokens()).isEqualTo(1L);
        assertThat(usageEvent.get().outputTokens()).isEqualTo(1L);
        assertThat(usageEvent.get().totalTokens()).isEqualTo(2L);
    }

    @Test
    void fallbackMayRunWhenPrimaryTransportFailsBeforeAnyToolResponse() {
        AtomicInteger primaryCalls = new AtomicInteger();
        AtomicInteger fallbackCalls = new AtomicInteger();
        AtomicInteger toolCalls = new AtomicInteger();
        AtomicInteger usageEvents = new AtomicInteger();
        AtomicReference<LlmUsageRecorded> usageEvent = new AtomicReference<>();
        ChatModel primary = prompt -> {
            primaryCalls.incrementAndGet();
            throw new TransientAiException("primary unavailable");
        };
        ChatModel fallback = prompt -> {
            fallbackCalls.incrementAndGet();
            return response("fallback-ok", 1, 1);
        };
        ModelExecutionSnapshot snapshot = snapshot();
        snapshot.setFallbackCapabilitiesJson(fallbackCapabilities());
        ModelExecutionService service = service(
            snapshot, 2, eventPublisher(usageEvent, usageEvents),
            effective -> "deepseek-v4-pro".equals(effective.getModel()) ? primary : fallback);

        LlmPort.CompletionResult result = service.complete(request(tool("unused", arguments -> {
            toolCalls.incrementAndGet();
            return "unused";
        })));

        assertThat(result.content()).isEqualTo("fallback-ok");
        assertThat(primaryCalls).hasValue(2);
        assertThat(fallbackCalls).hasValue(1);
        assertThat(toolCalls).hasValue(0);
        assertThat(usageEvents).hasValue(1);
        assertThat(usageEvent.get().model()).isEqualTo("deepseek-v4-flash");
    }

    @Test
    void toolHandlerFailureIsNeitherLlmRetriedNorFallbackTriggered() {
        AtomicInteger primaryCalls = new AtomicInteger();
        AtomicInteger fallbackCalls = new AtomicInteger();
        AtomicInteger toolCalls = new AtomicInteger();
        AtomicInteger usageEvents = new AtomicInteger();
        AtomicReference<LlmUsageRecorded> usageEvent = new AtomicReference<>();
        ChatModel primary = prompt -> {
            primaryCalls.incrementAndGet();
            return toolRequest("call-1", "fail_tool", "{}", 1, 1);
        };
        ChatModel fallback = prompt -> {
            fallbackCalls.incrementAndGet();
            return response("must-not-run", 1, 1);
        };
        ModelExecutionSnapshot snapshot = snapshot();
        snapshot.setFallbackCapabilitiesJson(fallbackCapabilities());
        ApplicationEventPublisher failingUsageListener = event -> {
            if (event instanceof LlmUsageRecorded usage) {
                usageEvents.incrementAndGet();
                usageEvent.set(usage);
                throw new IllegalStateException("telemetry unavailable");
            }
        };
        ModelExecutionService service = service(
            snapshot, 3, failingUsageListener,
            effective -> "deepseek-v4-pro".equals(effective.getModel()) ? primary : fallback);

        assertThatThrownBy(() -> service.complete(request(tool("fail_tool", arguments -> {
            toolCalls.incrementAndGet();
            throw new IllegalStateException("tool failed");
        }))))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("tool failed");

        assertThat(primaryCalls).hasValue(1);
        assertThat(fallbackCalls).hasValue(0);
        assertThat(toolCalls).hasValue(1);
        assertThat(usageEvents).hasValue(1);
        assertThat(usageEvent.get().totalTokens()).isEqualTo(2L);
    }

    @Test
    void deepSeekWireContinuationReplaysReasoningToolCallAndToolResultExactlyOnce() throws Exception {
        List<String> requestBodies = new ArrayList<>();
        AtomicInteger requests = new AtomicInteger();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/chat/completions", exchange -> {
            requestBodies.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            if (requests.incrementAndGet() == 1) {
                respond(exchange, 200, """
                    {"id":"deepseek-tool-1","object":"chat.completion","created":1,"model":"deepseek-v4-pro",
                     "choices":[{"index":0,"message":{"role":"assistant","content":null,
                       "reasoning_content":"I should use the tool.",
                       "tool_calls":[{"id":"call-1","type":"function","function":{"name":"double_value","arguments":"{\\\"value\\\":21}"}}]},
                       "finish_reason":"tool_calls"}],
                     "usage":{"prompt_tokens":2,"completion_tokens":1,"total_tokens":3}}
                    """);
            } else {
                respond(exchange, 200, """
                    {"id":"deepseek-tool-2","object":"chat.completion","created":2,"model":"deepseek-v4-pro",
                     "choices":[{"index":0,"message":{"role":"assistant","content":"The tool returned 42."},"finish_reason":"stop"}],
                     "usage":{"prompt_tokens":3,"completion_tokens":2,"total_tokens":5}}
                    """);
            }
        });
        server.start();

        int port = server.getAddress().getPort();
        CustomLlmEgressPolicy policy = new CustomLlmEgressPolicy(true, true, Set.of(port), Dns.SYSTEM);
        ModelCapabilityCatalog catalog = new ModelCapabilityCatalog();
        tools.jackson.databind.ObjectMapper objectMapper = new tools.jackson.databind.ObjectMapper();
        ModelCapabilityJson capabilityJson = new ModelCapabilityJson(objectMapper);
        SpringAiModelFactory factory = new SpringAiModelFactory(
            "sk-system",
            "http://127.0.0.1:" + port,
            policy,
            new EgressHttpClientFactory(policy),
            catalog,
            capabilityJson,
            objectMapper);
        ModelExecutionSnapshotService snapshotService = mock(ModelExecutionSnapshotService.class);
        ModelProfileService profileService = mock(ModelProfileService.class);
        when(snapshotService.require(1L)).thenReturn(snapshot());
        when(profileService.resolveApiKey(anyLong(), nullable(Long.class))).thenReturn(null);
        ModelExecutionService service = new ModelExecutionService(
            factory, snapshotService, profileService, capabilityJson, new LlmTransportRetry(2),
            mock(ApplicationEventPublisher.class));
        AtomicInteger toolCalls = new AtomicInteger();

        LlmPort.CompletionResult result = service.complete(request(tool("double_value", arguments -> {
            toolCalls.incrementAndGet();
            return "42";
        })));

        assertThat(result.content()).isEqualTo("The tool returned 42.");
        assertThat(toolCalls).hasValue(1);
        assertThat(requests).hasValue(2);
        assertThat(requestBodies.get(1))
            .contains("\"role\":\"assistant\"")
            .contains("\"reasoning_content\":\"I should use the tool.\"")
            .contains("\"tool_calls\"")
            .contains("\"role\":\"tool\"")
            .contains("42");
    }

    private ModelExecutionService service(
        ModelExecutionSnapshot snapshot,
        int attempts,
        ApplicationEventPublisher eventPublisher,
        java.util.function.Function<ModelExecutionSnapshot, ChatModel> modelForSnapshot
    ) {
        SpringAiModelFactory factory = mock(SpringAiModelFactory.class);
        ModelExecutionSnapshotService snapshotService = mock(ModelExecutionSnapshotService.class);
        ModelProfileService profileService = mock(ModelProfileService.class);
        when(snapshotService.require(1L)).thenReturn(snapshot);
        when(profileService.resolveApiKey(anyLong(), nullable(Long.class))).thenReturn(null);
        when(factory.chatModel(any(), nullable(String.class)))
            .thenAnswer(invocation -> modelForSnapshot.apply(invocation.getArgument(0)));
        when(factory.requestOptions(any(), any())).thenAnswer(invocation -> {
            ModelExecutionSnapshot effective = invocation.getArgument(0);
            return OpenAiChatOptions.builder().model(effective.getModel()).maxTokens(4096).build();
        });
        ModelCapabilityJson capabilityJson = capabilityJson();
        return new ModelExecutionService(
            factory,
            snapshotService,
            profileService,
            capabilityJson,
            new LlmTransportRetry(attempts),
            eventPublisher
        );
    }

    private ApplicationEventPublisher eventPublisher(
        AtomicReference<LlmUsageRecorded> captured,
        AtomicInteger eventCount
    ) {
        return event -> {
            if (event instanceof LlmUsageRecorded usage) {
                eventCount.incrementAndGet();
                captured.set(usage);
            }
        };
    }

    private LlmPort.ModelExecutionRequest request(LlmPort.ToolBinding tool) {
        return new LlmPort.ModelExecutionRequest(
            1L,
            "tool-contract",
            "tool.contract",
            LlmPort.ResponseMode.PLAIN_TEXT,
            List.of(new LlmPort.Message("user", "Run the tool.")),
            List.of(),
            List.of(tool)
        );
    }

    private LlmPort.ToolBinding tool(String name, LlmPort.ToolHandler handler) {
        return new LlmPort.ToolBinding(
            name,
            "Test tool.",
            "{\"type\":\"object\"}",
            handler
        );
    }

    private ModelExecutionSnapshot snapshot() {
        ModelExecutionSnapshot snapshot = new ModelExecutionSnapshot();
        snapshot.setId(1L);
        snapshot.setAccountId(7L);
        snapshot.setProfileId(9L);
        snapshot.setProvider("deepseek");
        snapshot.setModel("deepseek-v4-pro");
        snapshot.setReasoningLevel("AUTO");
        snapshot.setEffectiveParametersJson("{\"maxOutputTokens\":4096}");
        snapshot.setCapabilityVersion(ModelCapabilityCatalog.CAPABILITY_VERSION);
        snapshot.setModelCapabilityJson(capabilityJson().write(
            new ModelCapabilityCatalog().capability("deepseek", "deepseek-v4-pro")));
        snapshot.setFallbackCapabilitiesJson("[]");
        return snapshot;
    }

    private String fallbackCapabilities() {
        return capabilityJson().writeList(List.of(
            new ModelCapabilityCatalog().capability("deepseek", "deepseek-v4-flash")));
    }

    private ModelCapabilityJson capabilityJson() {
        return new ModelCapabilityJson(new tools.jackson.databind.ObjectMapper());
    }

    private void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (var output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }

    private ChatResponse toolRequest(
        String id,
        String name,
        String arguments,
        int inputTokens,
        int outputTokens
    ) {
        AssistantMessage toolRequest = AssistantMessage.builder()
            .content("")
            .toolCalls(List.of(new AssistantMessage.ToolCall(id, "function", name, arguments)))
            .build();
        return response(toolRequest, inputTokens, outputTokens);
    }

    private ChatResponse response(String content, int inputTokens, int outputTokens) {
        return response(new AssistantMessage(content), inputTokens, outputTokens);
    }

    private ChatResponse response(AssistantMessage message, int inputTokens, int outputTokens) {
        return new ChatResponse(
            List.of(new Generation(message)),
            ChatResponseMetadata.builder()
                .model("deepseek-v4-pro")
                .usage(new DefaultUsage(inputTokens, outputTokens, inputTokens + outputTokens))
                .build()
        );
    }
}
