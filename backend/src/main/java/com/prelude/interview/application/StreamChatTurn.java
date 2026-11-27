package com.prelude.interview.application;

import com.prelude.BusinessException;
import com.prelude.activity.SseSessionStream;
import com.prelude.identity.api.SessionValidity;
import com.prelude.interview.api.InterviewJudgeEventResponse;
import com.prelude.interview.application.port.InterviewTurnCommand;
import com.prelude.interview.application.port.InterviewTurnPort;
import com.prelude.interview.application.port.InterviewTurnResult;
import com.prelude.interview.application.port.JudgeOutcome;
import com.prelude.interview.application.port.JudgeResult;
import com.prelude.interview.domain.AssessmentStatus;
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

    public void execute(
        Long sessionId,
        String content,
        boolean autoStart,
        String authSessionId,
        SseSessionStream stream
    ) {
        execute(sessionId, content, autoStart, null, null, authSessionId, stream);
    }

    public void execute(
        Long sessionId,
        String content,
        boolean autoStart,
        Long retryOfMessageId,
        Long supersedesMessageId,
        String authSessionId,
        SseSessionStream stream
    ) {
        long accountId = sessionAccess.currentAccountId();
        sseTaskExecutor.execute(() -> runTurn(
            sessionId, accountId, authSessionId, content, autoStart,
            retryOfMessageId, supersedesMessageId, stream));
    }

    private void runTurn(
        Long sessionId,
        long accountId,
        String authSessionId,
        String content,
        boolean autoStart,
        Long retryOfMessageId,
        Long supersedesMessageId,
        SseSessionStream stream
    ) {
        try {
            InterviewTurnResult result = interviewTurnPort.execute(
                new InterviewTurnCommand(
                    sessionId,
                    accountId,
                    content,
                    autoStart,
                    retryOfMessageId,
                    supersedesMessageId
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
                sendJudgeEvent(stream, interviewTurnPort.judgeAndPersist(sessionId, messageId));
                stream.complete();
            } catch (RuntimeException error) {
                log.error("Error in async judge task", error);
                stream.complete();
            }
        });
    }

    private void sendJudgeEvent(SseSessionStream stream, JudgeOutcome outcome) {
        if (outcome.status() == JudgeOutcome.Status.SUPERSEDED) {
            return;
        }
        try {
            JudgeResult result = outcome.result();
            stream.send("judge", new InterviewJudgeEventResponse(
                result == null ? null : result.score(),
                result == null ? null : result.hint(),
                switch (outcome.status()) {
                    case COMPLETED -> AssessmentStatus.SCORED;
                    case BUSY -> AssessmentStatus.PENDING;
                    case UNSCORED -> AssessmentStatus.UNSCORED;
                    case SUPERSEDED -> AssessmentStatus.PENDING;
                },
                outcome.error()
            ));
        } catch (RuntimeException error) {
            log.warn("Failed to send judge event via SSE", error);
        }
    }
}
