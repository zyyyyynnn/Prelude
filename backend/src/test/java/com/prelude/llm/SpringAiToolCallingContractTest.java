package com.prelude.llm;

import com.prelude.LlmServerException;
import com.prelude.llm.api.LlmPort;
import com.prelude.llm.api.LlmUsageRecorded;
import com.prelude.llm.persistence.ModelExecutionSnapshot;
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

import java.util.List;
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

    @Test
    void continuationTransportRetryDoesNotReplayCommittedToolAndAggregatesUsage() {
        AtomicInteger modelCalls = new AtomicInteger();
        AtomicInteger toolCalls = new AtomicInteger();
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
        ApplicationEventPublisher events = eventPublisher(usageEvent);
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
        snapshot.setFallbackModelsJson("[\"deepseek-v4-flash\"]");
        ModelExecutionService service = service(
            snapshot, 2, mock(ApplicationEventPublisher.class),
            effective -> "deepseek-v4-pro".equals(effective.getModel()) ? primary : fallback);

        assertThatThrownBy(() -> service.complete(request(tool("commit_side_effect", arguments -> {
            toolCalls.incrementAndGet();
            return "committed";
        }))))
            .isInstanceOf(LlmServerException.class);

        assertThat(primaryCalls).hasValue(3);
        assertThat(fallbackCalls).hasValue(0);
        assertThat(toolCalls).hasValue(1);
    }

    @Test
    void fallbackMayRunWhenPrimaryTransportFailsBeforeAnyToolResponse() {
        AtomicInteger primaryCalls = new AtomicInteger();
        AtomicInteger fallbackCalls = new AtomicInteger();
        AtomicInteger toolCalls = new AtomicInteger();
        ChatModel primary = prompt -> {
            primaryCalls.incrementAndGet();
            throw new TransientAiException("primary unavailable");
        };
        ChatModel fallback = prompt -> {
            fallbackCalls.incrementAndGet();
            return response("fallback-ok", 1, 1);
        };
        ModelExecutionSnapshot snapshot = snapshot();
        snapshot.setFallbackModelsJson("[\"deepseek-v4-flash\"]");
        ModelExecutionService service = service(
            snapshot, 2, mock(ApplicationEventPublisher.class),
            effective -> "deepseek-v4-pro".equals(effective.getModel()) ? primary : fallback);

        LlmPort.CompletionResult result = service.complete(request(tool("unused", arguments -> {
            toolCalls.incrementAndGet();
            return "unused";
        })));

        assertThat(result.content()).isEqualTo("fallback-ok");
        assertThat(primaryCalls).hasValue(2);
        assertThat(fallbackCalls).hasValue(1);
        assertThat(toolCalls).hasValue(0);
    }

    @Test
    void toolHandlerFailureIsNeitherLlmRetriedNorFallbackTriggered() {
        AtomicInteger primaryCalls = new AtomicInteger();
        AtomicInteger fallbackCalls = new AtomicInteger();
        AtomicInteger toolCalls = new AtomicInteger();
        ChatModel primary = prompt -> {
            primaryCalls.incrementAndGet();
            return toolRequest("call-1", "fail_tool", "{}", 1, 1);
        };
        ChatModel fallback = prompt -> {
            fallbackCalls.incrementAndGet();
            return response("must-not-run", 1, 1);
        };
        ModelExecutionSnapshot snapshot = snapshot();
        snapshot.setFallbackModelsJson("[\"deepseek-v4-flash\"]");
        ModelExecutionService service = service(
            snapshot, 3, mock(ApplicationEventPublisher.class),
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
        return new ModelExecutionService(
            factory,
            snapshotService,
            profileService,
            new ModelCapabilityCatalog(),
            new LlmTransportRetry(attempts),
            eventPublisher
        );
    }

    private ApplicationEventPublisher eventPublisher(AtomicReference<LlmUsageRecorded> captured) {
        return event -> {
            if (event instanceof LlmUsageRecorded usage) {
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
        snapshot.setFallbackModelsJson("[]");
        return snapshot;
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
