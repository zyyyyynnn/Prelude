package com.prelude.llm;

import com.prelude.llm.LlmSelection;

import java.time.Duration;
import java.util.List;
import java.util.Map;

public record ChatRequest(
    Long accountId,
    Long sessionId,
    LlmPurpose purpose,
    String promptId,
    List<Map<String, String>> messages,
    LlmSelection selection,
    Duration timeout,
    Integer maxTokens,
    Map<String, Object> extraParams,
    List<LlmAttachment> attachments
) {
    public ChatRequest {
        messages = messages == null ? List.of() : List.copyOf(messages);
        timeout = timeout == null ? Duration.ofSeconds(60) : timeout;
        extraParams = extraParams == null ? Map.of() : Map.copyOf(extraParams);
        attachments = attachments == null ? List.of() : List.copyOf(attachments);
    }

    public static ChatRequest currentUser(
        Long accountId,
        LlmPurpose purpose,
        String promptId,
        List<Map<String, String>> messages
    ) {
        return new ChatRequest(
            accountId, null, purpose, promptId, messages, null, null, null, null, null
        );
    }

    public static ChatRequest snapshot(
        Long accountId,
        Long sessionId,
        LlmPurpose purpose,
        String promptId,
        List<Map<String, String>> messages,
        LlmSelection selection,
        Map<String, Object> extraParams
    ) {
        return new ChatRequest(
            accountId, sessionId, purpose, promptId, messages, selection, null, null, extraParams, null
        );
    }

    public static ChatRequest snapshot(
        Long accountId,
        Long sessionId,
        LlmPurpose purpose,
        String promptId,
        List<Map<String, String>> messages,
        LlmSelection selection,
        Map<String, Object> extraParams,
        List<LlmAttachment> attachments
    ) {
        return new ChatRequest(
            accountId, sessionId, purpose, promptId, messages, selection, null, null, extraParams, attachments
        );
    }
}
