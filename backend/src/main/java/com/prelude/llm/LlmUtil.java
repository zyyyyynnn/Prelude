package com.prelude.llm;

import com.prelude.UserContext;
import com.prelude.llm.ChatPort;
import com.prelude.llm.ChatRequest;
import com.prelude.llm.LlmPurpose;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

@Component
@RequiredArgsConstructor
public class LlmUtil {

    private final ChatPort chatPort;

    public String chat(String systemPrompt, String userPrompt) {
        return chat(List.of(
            Map.of("role", "system", "content", systemPrompt),
            Map.of("role", "user", "content", userPrompt)
        ));
    }

    public String chat(List<Map<String, String>> messages) {
        return chatPort.complete(ChatRequest.currentUser(
            UserContext.getCurrentUserId(), LlmPurpose.CHAT, PromptIds.CHAT, messages
        ));
    }

    public void streamChat(List<Map<String, String>> messages, Consumer<String> onDelta) {
        chatPort.stream(ChatRequest.currentUser(
            UserContext.getCurrentUserId(), LlmPurpose.CHAT, PromptIds.CHAT, messages
        ), onDelta);
    }
}
