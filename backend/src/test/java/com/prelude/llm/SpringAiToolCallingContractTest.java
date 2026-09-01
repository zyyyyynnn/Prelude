package com.prelude.llm;

import com.prelude.llm.api.LlmPort;
import com.prelude.llm.persistence.ModelExecutionSnapshot;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatOptions;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SpringAiToolCallingContractTest {

    @Test
    void defaultChatClientToolCallingAdvisorExecutesTheBoundToolAndContinuesTheModelLoop() {
        AtomicInteger modelCalls = new AtomicInteger();
        AtomicInteger toolCalls = new AtomicInteger();
        AtomicReference<String> toolArguments = new AtomicReference<>();
        ChatModel model = new ChatModel() {
            private final OpenAiChatOptions defaults = OpenAiChatOptions.builder().model("gpt-5.4").build();

            @Override
            public ChatResponse call(Prompt prompt) {
                int call = modelCalls.incrementAndGet();
                if (call == 1) {
                    assertThat(prompt.getOptions())
                        .isInstanceOf(org.springframework.ai.model.tool.ToolCallingChatOptions.class);
                    assertThat(((org.springframework.ai.model.tool.ToolCallingChatOptions) prompt.getOptions())
                        .getToolCallbacks()).hasSize(1);
                    AssistantMessage toolRequest = AssistantMessage.builder()
                        .content("")
                        .toolCalls(List.of(new AssistantMessage.ToolCall(
                            "call-1", "function", "double_value", "{\"value\":21}")))
                        .build();
                    return new ChatResponse(List.of(new Generation(toolRequest)));
                }
                assertThat(prompt.getInstructions())
                    .anyMatch(message -> message instanceof ToolResponseMessage response
                        && response.getResponses().stream().anyMatch(tool -> "42".equals(tool.responseData())));
                return response("The tool returned 42.");
            }

            @Override
            public org.springframework.ai.chat.prompt.ChatOptions getOptions() {
                return defaults;
            }
        };

        SpringAiModelFactory factory = mock(SpringAiModelFactory.class);
        ModelExecutionSnapshotService snapshotService = mock(ModelExecutionSnapshotService.class);
        ModelProfileService profileService = mock(ModelProfileService.class);
        ModelExecutionSnapshot snapshot = snapshot();
        when(snapshotService.require(1L)).thenReturn(snapshot);
        when(profileService.resolveApiKey(anyLong(), nullable(Long.class))).thenReturn(null);
        when(factory.chatModel(any(), nullable(String.class))).thenReturn(model);
        when(factory.requestOptions(any(), any())).thenReturn(
            OpenAiChatOptions.builder().model("gpt-5.4").build());

        ModelExecutionService service = new ModelExecutionService(
            factory, snapshotService, profileService, new ModelCapabilityCatalog(), 1);
        LlmPort.ToolBinding tool = new LlmPort.ToolBinding(
            "double_value",
            "Doubles the supplied integer.",
            "{\"type\":\"object\",\"properties\":{\"value\":{\"type\":\"integer\"}},\"required\":[\"value\"]}",
            arguments -> {
                toolCalls.incrementAndGet();
                toolArguments.set(arguments);
                return "42";
            });

        LlmPort.CompletionResult result = service.complete(new LlmPort.ModelExecutionRequest(
            1L,
            "tool-contract",
            "tool.contract",
            LlmPort.ResponseMode.PLAIN_TEXT,
            List.of(new LlmPort.Message("user", "Double 21.")),
            List.of(),
            List.of(tool)));

        assertThat(modelCalls).hasValue(2);
        assertThat(toolCalls).hasValue(1);
        assertThat(toolArguments.get()).isEqualTo("{\"value\":21}");
        assertThat(result.content()).isEqualTo("The tool returned 42.");
    }

    private ModelExecutionSnapshot snapshot() {
        ModelExecutionSnapshot snapshot = new ModelExecutionSnapshot();
        snapshot.setId(1L);
        snapshot.setAccountId(7L);
        snapshot.setProfileId(9L);
        snapshot.setProvider("openai");
        snapshot.setModel("gpt-5.4");
        snapshot.setReasoningLevel("AUTO");
        snapshot.setEffectiveParametersJson("{}");
        snapshot.setCapabilityVersion(ModelCapabilityCatalog.CAPABILITY_VERSION);
        snapshot.setFallbackModelsJson("[]");
        return snapshot;
    }

    private ChatResponse response(String content) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(content))));
    }
}
