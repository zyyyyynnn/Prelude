package com.interview.platform.llm;

import com.interview.shared.web.UserContext;
import com.interview.platform.llm.LlmRouter;
import com.interview.platform.llm.LlmSelection;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LlmChatGatewayTest {

    private final LlmRouter llmRouter = mock(LlmRouter.class);
    private final LlmChatGateway gateway = new LlmChatGateway(llmRouter);

    @AfterEach
    void tearDown() {
        UserContext.remove();
    }

    @Test
    void completeUsesSelectionSnapshotAndRestoresCallingContext() {
        UserContext.setCurrentUserId(7L);
        UserContext.setCurrentSessionId(9L);
        List<Map<String, String>> messages = List.of(Map.of("role", "user", "content", "hello"));
        LlmSelection selection = new LlmSelection("openai-responses", "model-a");
        when(llmRouter.chatWithSnapshot(
            eq("openai-responses"),
            eq("model-a"),
            eq(messages),
            eq(Map.of("response_format", Map.of("type", "json_object")))
        )).thenReturn("ok");

        String result = gateway.complete(ChatRequest.snapshot(
            42L,
            LlmPurpose.JUDGE,
            PromptVersions.JUDGE,
            PromptVersions.V1,
            messages,
            selection,
            Map.of("response_format", Map.of("type", "json_object"))
        ));

        assertThat(result).isEqualTo("ok");
        assertThat(UserContext.getCurrentUserId()).isEqualTo(7L);
        assertThat(UserContext.getCurrentSessionId()).isEqualTo(9L);
        verify(llmRouter).chatWithSnapshot(
            "openai-responses",
            "model-a",
            messages,
            Map.of("response_format", Map.of("type", "json_object"))
        );
    }
}
