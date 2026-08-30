package com.prelude.llm;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.function.Consumer;
import java.util.function.Supplier;

@Slf4j
@Service
@RequiredArgsConstructor
public class LlmChatGateway implements ChatPort {

    private final LlmRouter llmRouter;

    @Override
    public String complete(ChatRequest request) {
        logRequest(request);
        return withInvocationContext(request.sessionId(), () -> {
            LlmSelection selection = request.selection();
            if (selection == null) {
                return llmRouter.chat(request.accountId(), request.messages());
            }
            return llmRouter.chatWithSnapshot(
                request.accountId(),
                selection.providerKey(),
                selection.model(),
                request.messages(),
                request.extraParams(),
                request.attachments()
            );
        });
    }

    @Override
    public void stream(ChatRequest request, Consumer<String> onDelta) {
        logRequest(request);
        withInvocationContext(request.sessionId(), () -> {
            LlmSelection selection = request.selection();
            if (selection == null) {
                llmRouter.stream(request.accountId(), request.messages(), onDelta);
            } else {
                llmRouter.streamWithSnapshot(
                    request.accountId(),
                    selection.providerKey(),
                    selection.model(),
                    request.messages(),
                    onDelta,
                    request.extraParams(),
                    request.attachments()
                );
            }
            return null;
        });
    }

    private void logRequest(ChatRequest request) {
        LlmSelection selection = request.selection();
        log.info(
            "llm_request accountId={} sessionId={} purpose={} promptId={} provider={} model={} timeoutMs={} maxTokens={}",
            request.accountId(),
            request.sessionId(),
            request.purpose(),
            request.promptId(),
            selection == null ? "current" : selection.providerKey(),
            selection == null ? "current" : selection.model(),
            request.timeout().toMillis(),
            request.maxTokens()
        );
    }

    private <T> T withInvocationContext(Long sessionId, Supplier<T> action) {
        Long previousSessionId = LlmInvocationContext.getCurrentSessionId();
        if (sessionId != null) {
            LlmInvocationContext.setCurrentSessionId(sessionId);
        }
        try {
            return action.get();
        } finally {
            LlmInvocationContext.clear();
            if (previousSessionId != null) {
                LlmInvocationContext.setCurrentSessionId(previousSessionId);
            }
        }
    }
}
