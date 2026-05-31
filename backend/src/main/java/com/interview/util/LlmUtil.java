package com.interview.util;

import com.interview.llm.LlmRouter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

@Component
@RequiredArgsConstructor
public class LlmUtil {

    private final LlmRouter llmRouter;

    public String chat(String systemPrompt, String userPrompt) {
        return chat(List.of(
            Map.of("role", "system", "content", systemPrompt),
            Map.of("role", "user", "content", userPrompt)
        ));
    }

    public String chat(List<Map<String, String>> messages) {
        return llmRouter.chatCurrentUser(messages);
    }

    public void streamChat(List<Map<String, String>> messages, Consumer<String> onDelta) {
        llmRouter.streamCurrentUser(messages, onDelta);
    }
}
