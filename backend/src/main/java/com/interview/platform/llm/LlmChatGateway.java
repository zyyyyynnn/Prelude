package com.interview.platform.llm;

import com.interview.shared.web.UserContext;
import com.interview.platform.llm.LlmRouter;
import com.interview.platform.llm.LlmSelection;
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
        return withUserContext(request.userId(), () -> {
            LlmSelection selection = request.selection();
            if (selection == null) {
                return llmRouter.chatCurrentUser(request.messages());
            }
            return llmRouter.chatWithSnapshot(
                selection.providerKey(),
                selection.model(),
                request.messages(),
                request.extraParams()
            );
        });
    }

    @Override
    public void stream(ChatRequest request, Consumer<String> onDelta) {
        logRequest(request);
        withUserContext(request.userId(), () -> {
            LlmSelection selection = request.selection();
            if (selection == null) {
                llmRouter.streamCurrentUser(request.messages(), onDelta);
            } else {
                llmRouter.streamWithSnapshot(
                    selection.providerKey(),
                    selection.model(),
                    request.messages(),
                    onDelta,
                    request.extraParams()
                );
            }
            return null;
        });
    }

    private void logRequest(ChatRequest request) {
        LlmSelection selection = request.selection();
        log.info(
            "llm_request userId={} purpose={} promptId={} promptVersion={} provider={} model={} timeoutMs={} maxTokens={}",
            request.userId(),
            request.purpose(),
            request.promptId(),
            request.promptVersion(),
            selection == null ? "current" : selection.providerKey(),
            selection == null ? "current" : selection.model(),
            request.timeout().toMillis(),
            request.maxTokens()
        );
    }

    private <T> T withUserContext(Long userId, Supplier<T> action) {
        Long previousUserId = UserContext.getCurrentUserId();
        Long previousSessionId = UserContext.getCurrentSessionId();
        if (userId != null) {
            UserContext.setCurrentUserId(userId);
        }
        try {
            return action.get();
        } finally {
            UserContext.remove();
            if (previousUserId != null) {
                UserContext.setCurrentUserId(previousUserId);
            }
            if (previousSessionId != null) {
                UserContext.setCurrentSessionId(previousSessionId);
            }
        }
    }
}
