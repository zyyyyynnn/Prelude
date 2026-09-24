package com.prelude.interview.application;

import com.prelude.BusinessException;
import com.prelude.activity.SseSessionStream;
import com.prelude.identity.api.SessionValidity;
import com.prelude.interview.application.port.InterviewTurnCommand;
import com.prelude.interview.application.port.InterviewTurnPort;
import com.prelude.interview.application.port.InterviewTurnResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.concurrent.Executor;

@Slf4j
@Service
@RequiredArgsConstructor
public class StreamChatTurn {

    private final InterviewSessionAccess sessionAccess;
    private final InterviewTurnPort interviewTurnPort;
    private final SessionValidity sessionValidity;
    @Qualifier("sseTaskExecutor")
    private final Executor sseTaskExecutor;

    /**
     * Streams one chat turn over a channel the caller opened. The use case authorizes the
     * turn and drives the model call; it never constructs the transport itself.
     */
    public void execute(
        Long sessionId,
        String content,
        boolean autoStart,
        String authSessionId,
        SseSessionStream stream
    ) {
        long accountId = sessionAccess.currentAccountId();
        sseTaskExecutor.execute(() -> runTurn(sessionId, accountId, authSessionId, content, autoStart, stream));
    }

    private void runTurn(
        Long sessionId,
        long accountId,
        String authSessionId,
        String content,
        boolean autoStart,
        SseSessionStream stream
    ) {
        try {
            InterviewTurnResult result = interviewTurnPort.execute(
                new InterviewTurnCommand(
                    sessionId,
                    accountId,
                    content,
                    autoStart,
                    true
                ),
                delta -> {
                    // Natural send boundary: a revoked session stops the stream
                    // instead of continuing to emit authenticated business data.
                    if (!sessionValidity.isActive(authSessionId, accountId)) {
                        throw BusinessException.unauthorized("登录已失效，请重新登录");
                    }
                    stream.send("message", delta);
                }
            );
            if (result.userTurn() == null) {
                stream.complete();
                return;
            }
            triggerAsyncJudge(result.session().sessionId(), result.userTurn().messageId(), stream);
            interviewTurnPort.summarizeIfNeeded(result.session().sessionId());
        } catch (RuntimeException error) {
            String message = error.getMessage() == null ? "连接已断开，请重试" : error.getMessage();
            stream.completeWithError(message);
        }
    }

    private void triggerAsyncJudge(Long sessionId, Long messageId, SseSessionStream stream) {
        sseTaskExecutor.execute(() -> {
            try {
                interviewTurnPort.judgeAndPersist(sessionId, messageId)
                    .ifPresent(result -> sendJudgeEvent(stream, result.json()));
                stream.complete();
            } catch (RuntimeException error) {
                log.error("Error in async judge task", error);
                stream.complete();
            }
        });
    }

    private void sendJudgeEvent(SseSessionStream stream, String judgeJson) {
        try {
            stream.send("judge", judgeJson);
        } catch (RuntimeException error) {
            log.warn("Failed to send judge event via SSE", error);
        }
    }
}
