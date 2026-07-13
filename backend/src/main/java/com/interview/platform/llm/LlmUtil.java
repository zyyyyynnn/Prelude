package com.interview.platform.llm;

import com.interview.shared.web.UserContext;
import com.interview.platform.llm.ChatPort;
import com.interview.platform.llm.ChatRequest;
import com.interview.platform.llm.LlmPurpose;
import com.interview.platform.llm.PromptVersions;
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
            UserContext.getCurrentUserId(), LlmPurpose.CHAT, PromptVersions.CHAT, PromptVersions.V1, messages
        ));
    }

    public void streamChat(List<Map<String, String>> messages, Consumer<String> onDelta) {
        chatPort.stream(ChatRequest.currentUser(
            UserContext.getCurrentUserId(), LlmPurpose.CHAT, PromptVersions.CHAT, PromptVersions.V1, messages
        ), onDelta);
    }
}
