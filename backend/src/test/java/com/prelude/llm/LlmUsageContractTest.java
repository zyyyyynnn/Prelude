package com.prelude.llm;

import com.prelude.llm.api.LlmPort;
import com.prelude.test.ExceptionFixtures;
import com.prelude.test.LlmFixtures;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.metadata.EmptyUsage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.retry.TransientAiException;
import org.springframework.context.ApplicationEventPublisher;
import reactor.core.publisher.Flux;

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

class LlmUsageContractTest {

    @Test
    void successfulCompletionEmitsOneAuthoritativeUsageEvent() {
        AtomicInteger events = new AtomicInteger();
        AtomicReference<LlmFixtures.UsageRecordView> captured = new AtomicReference<>();
        ApplicationEventPublisher publisher = event -> {
            if (LlmFixtures.isUsageRecorded(event)) {
                events.incrementAndGet();
                captured.set(LlmFixtures.asUsageRecord(event));
            }
        };
        ModelExecutionService service = service(
            prompt -> response("ok", 7, 3), publisher);

        LlmPort.CompletionResult result = service.complete(request());

        assertThat(result.content()).isEqualTo("ok");
        assertThat(events).hasValue(1);
        assertThat(captured.get()).satisfies(usage -> {
            assertThat(usage.accountId()).isEqualTo(7L);
            assertThat(usage.snapshotId()).isEqualTo(1L);
            assertThat(usage.purpose()).isEqualTo("usage-contract");
            assertThat(usage.promptId()).isEqualTo("usage.contract");
            assertThat(usage.provider()).isEqualTo("deepseek");
            assertThat(usage.model()).isEqualTo("deepseek-v4-pro");
            assertThat(usage.inputTokens()).isEqualTo(7L);
            assertThat(usage.outputTokens()).isEqualTo(3L);
            assertThat(usage.totalTokens()).isEqualTo(10L);
            assertThat(usage.occurredAt()).isNotNull();
            assertThat(usage.estimatedCost()).isNull();
        });
    }

    @Test
    void successfulCompletionWithEmptyUsageEmitsNoUsageEvent() {
        AtomicInteger events = new AtomicInteger();
        ModelExecutionService service = service(
            prompt -> response("ok", new EmptyUsage()),
            event -> {
                if (LlmFixtures.isUsageRecorded(event)) {
                    events.incrementAndGet();
                }
            });

        LlmPort.CompletionResult result = service.complete(request());

        assertThat(result.content()).isEqualTo("ok");
        assertThat(result.usage().inputTokens()).isNull();
        assertThat(result.usage().outputTokens()).isNull();
        assertThat(result.usage().totalTokens()).isNull();
        assertThat(events).hasValue(0);
    }

    @Test
    void authoritativeZeroTokenUsageStillEmitsOneUsageEvent() {
        AtomicInteger events = new AtomicInteger();
        AtomicReference<LlmFixtures.UsageRecordView> captured = new AtomicReference<>();
        ModelExecutionService service = service(
            prompt -> response("ok", new DefaultUsage(0, 0, 0)),
            event -> {
                if (LlmFixtures.isUsageRecorded(event)) {
                    events.incrementAndGet();
                    captured.set(LlmFixtures.asUsageRecord(event));
                }
            });

        LlmPort.CompletionResult result = service.complete(request());

        assertThat(result.content()).isEqualTo("ok");
        assertThat(events).hasValue(1);
        assertThat(captured.get().inputTokens()).isZero();
        assertThat(captured.get().outputTokens()).isZero();
        assertThat(captured.get().totalTokens()).isZero();
    }

    @Test
    void successfulStreamEmitsUsageOnlyAfterTerminalProviderMetadataArrives() {
        AtomicInteger events = new AtomicInteger();
        AtomicReference<LlmFixtures.UsageRecordView> captured = new AtomicReference<>();
        ChatModel model = new ChatModel() {
            @Override
            public ChatResponse call(Prompt prompt) {
                throw new UnsupportedOperationException();
            }

            @Override
            public Flux<ChatResponse> stream(Prompt prompt) {
                return Flux.just(response("chunk", 5, 2));
            }
        };
        ModelExecutionService service = service(model, event -> {
            if (LlmFixtures.isUsageRecorded(event)) {
                events.incrementAndGet();
                captured.set(LlmFixtures.asUsageRecord(event));
            }
        });
        StringBuilder output = new StringBuilder();

        service.stream(request(), output::append);

        assertThat(output).hasToString("chunk");
        assertThat(events).hasValue(1);
        assertThat(captured.get().inputTokens()).isEqualTo(5L);
        assertThat(captured.get().outputTokens()).isEqualTo(2L);
        assertThat(captured.get().totalTokens()).isEqualTo(7L);
    }

    @Test
    void failedStreamStillEmitsUsageThatArrivedBeforeTheFailure() {
        AtomicInteger events = new AtomicInteger();
        AtomicReference<LlmFixtures.UsageRecordView> captured = new AtomicReference<>();
        ChatModel model = new ChatModel() {
            @Override
            public ChatResponse call(Prompt prompt) {
                throw new UnsupportedOperationException();
            }

            @Override
            public Flux<ChatResponse> stream(Prompt prompt) {
                return Flux.concat(
                    Flux.just(response("partial", 4, 2)),
                    Flux.error(new TransientAiException("stream failed after usage")));
            }
        };
        ModelExecutionService service = service(model, event -> {
            if (LlmFixtures.isUsageRecorded(event)) {
                events.incrementAndGet();
                captured.set(LlmFixtures.asUsageRecord(event));
            }
        });
        StringBuilder output = new StringBuilder();

        ExceptionFixtures.assertLlmServerException(() -> service.stream(request(), output::append));

        assertThat(output).hasToString("partial");
        assertThat(events).hasValue(1);
        assertThat(captured.get().inputTokens()).isEqualTo(4L);
        assertThat(captured.get().outputTokens()).isEqualTo(2L);
        assertThat(captured.get().totalTokens()).isEqualTo(6L);
    }

    @Test
    void failedStreamWithOnlyEmptyUsageEmitsNoUsageEvent() {
        AtomicInteger events = new AtomicInteger();
        ChatModel model = new ChatModel() {
            @Override
            public ChatResponse call(Prompt prompt) {
                throw new UnsupportedOperationException();
            }

            @Override
            public Flux<ChatResponse> stream(Prompt prompt) {
                return Flux.concat(
                    Flux.just(response("partial", new EmptyUsage())),
                    Flux.error(new TransientAiException("stream failed before provider usage")));
            }
        };
        ModelExecutionService service = service(model, event -> {
            if (LlmFixtures.isUsageRecorded(event)) {
                events.incrementAndGet();
            }
        });
        StringBuilder output = new StringBuilder();

        ExceptionFixtures.assertLlmServerException(() -> service.stream(request(), output::append));

        assertThat(output).hasToString("partial");
        assertThat(events).hasValue(0);
    }

    @Test
    void usageListenerFailureCannotTurnSuccessfulModelExecutionIntoFailure() {
        ModelExecutionService service = service(
            prompt -> response("business-ok", 1, 1),
            event -> {
                throw new IllegalStateException("telemetry unavailable");
            });

        LlmPort.CompletionResult result = service.complete(request());

        assertThat(result.content()).isEqualTo("business-ok");
        assertThat(result.usage().totalTokens()).isEqualTo(2L);
    }

    @Test
    void transportFailureBeforeAnyProviderResponseEmitsNoUsage() {
        AtomicInteger events = new AtomicInteger();
        ModelExecutionService service = service(
            prompt -> {
                throw new TransientAiException("provider unavailable");
            },
            event -> {
                if (LlmFixtures.isUsageRecorded(event)) {
                    events.incrementAndGet();
                }
            });

        ExceptionFixtures.assertLlmServerException(() -> service.complete(request()));
        assertThat(events).hasValue(0);
    }

    private ModelExecutionService service(ChatModel model, ApplicationEventPublisher publisher) {
        SpringAiModelFactory factory = mock(SpringAiModelFactory.class);
        ModelExecutionSnapshotService snapshotService = mock(ModelExecutionSnapshotService.class);
        ModelProfileService profileService = mock(ModelProfileService.class);
        ProviderCredentialResolver credentialResolver = mock(ProviderCredentialResolver.class);
        when(snapshotService.require(1L)).thenReturn(
            LlmFixtures.snapshotWithDefaults(1L, 7L, 9L, "deepseek", "deepseek-v4-pro", "AUTO", 4096));
        when(credentialResolver.resolve(anyLong(), nullable(Long.class))).thenReturn(null);
        when(factory.chatModel(any(), nullable(String.class))).thenReturn(model);
        when(factory.requestOptions(any(), any())).thenReturn(
            OpenAiChatOptions.builder().model("deepseek-v4-pro").maxTokens(4096).build());
        ModelCapabilityJson capabilityJson = new ModelCapabilityJson(new tools.jackson.databind.ObjectMapper());
        return new ModelExecutionService(factory, snapshotService, credentialResolver, capabilityJson,
            new LlmTransportRetry(1),
            publisher
        );
    }

    private LlmPort.ModelExecutionRequest request() {
        return LlmFixtures.executionRequest(
            1L, "usage-contract", "usage.contract", LlmFixtures.responseModePlainText(), "hello");
    }

    private ChatResponse response(String content, int inputTokens, int outputTokens) {
        return response(content, new DefaultUsage(inputTokens, outputTokens, inputTokens + outputTokens));
    }

    private ChatResponse response(String content, org.springframework.ai.chat.metadata.Usage usage) {
        return new ChatResponse(
            List.of(new Generation(new AssistantMessage(content))),
            ChatResponseMetadata.builder()
                .model("deepseek-v4-pro")
                .usage(usage)
                .build()
        );
    }
}
