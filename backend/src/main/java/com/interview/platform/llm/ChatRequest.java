package com.interview.platform.llm;

import com.interview.platform.llm.LlmSelection;

import java.time.Duration;
import java.util.List;
import java.util.Map;

public record ChatRequest(
    Long userId,
    LlmPurpose purpose,
    String promptId,
    String promptVersion,
    List<Map<String, String>> messages,
    LlmSelection selection,
    Duration timeout,
    Integer maxTokens,
    Map<String, Object> extraParams
) {
    public ChatRequest {
        messages = messages == null ? List.of() : List.copyOf(messages);
        timeout = timeout == null ? Duration.ofSeconds(60) : timeout;
        extraParams = extraParams == null ? Map.of() : Map.copyOf(extraParams);
    }

    public static ChatRequest currentUser(
        Long userId,
        LlmPurpose purpose,
        String promptId,
        String promptVersion,
        List<Map<String, String>> messages
    ) {
        return new ChatRequest(
            userId, purpose, promptId, promptVersion, messages, null, null, null, null
        );
    }

    public static ChatRequest snapshot(
        Long userId,
        LlmPurpose purpose,
        String promptId,
        String promptVersion,
        List<Map<String, String>> messages,
        LlmSelection selection,
        Map<String, Object> extraParams
    ) {
        return new ChatRequest(
            userId, purpose, promptId, promptVersion, messages, selection, null, null, extraParams
        );
    }
}
