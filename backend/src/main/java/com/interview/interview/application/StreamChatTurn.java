package com.interview.interview.application;

import com.interview.shared.web.UserContext;
import com.interview.interview.domain.InterviewMessage;
import com.interview.interview.domain.InterviewSession;
import com.interview.platform.realtime.RealtimePort;
import com.interview.platform.realtime.SseSessionStream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.concurrent.Executor;

@Slf4j
@Service
@RequiredArgsConstructor
public class StreamChatTurn {

    private static final long SSE_TIMEOUT_MS = 120000L;

    private final InterviewSessionAccess sessionAccess;
    private final RunInterviewTurn runInterviewTurn;
    private final InterviewJudgeService interviewJudgeService;
    private final InterviewSummaryService interviewSummaryService;
    @Qualifier("sseTaskExecutor")
    private final Executor sseTaskExecutor;
    private final RealtimePort realtimePort;

    public SseEmitter execute(Long sessionId, String content, boolean autoStart) {
        Long userId = sessionAccess.currentUserId();
        SseSessionStream stream = SseSessionStream.open(realtimePort, sessionId, SSE_TIMEOUT_MS);
        stream.emitter().onTimeout(() -> completeWithError(stream, "连接超时，请重试"));
        stream.emitter().onError(error -> stream.complete());

        sseTaskExecutor.execute(() -> runTurn(sessionId, userId, content, autoStart, stream));
        return stream.emitter();
    }

    private void runTurn(
        Long sessionId,
        Long userId,
        String content,
        boolean autoStart,
        SseSessionStream stream
    ) {
        UserContext.setCurrentUserId(userId);
        UserContext.setCurrentSessionId(sessionId);
        try {
            InterviewTurnResult result = runInterviewTurn.execute(
                new InterviewTurnCommand(
                    sessionId,
                    userId,
                    content,
                    autoStart,
                    true
                ),
                delta -> stream.send("message", delta)
            );
            if (result.userMessage() == null) {
                stream.complete();
                return;
            }
            triggerAsyncJudge(result.session(), result.userMessage(), stream);
            interviewSummaryService.triggerAsyncSummarizeIfNeeded(result.session(), false);
        } catch (RuntimeException error) {
            completeWithError(stream, error.getMessage() == null ? "连接已断开，请重试" : error.getMessage());
        } finally {
            UserContext.remove();
        }
    }

    private void completeWithError(SseSessionStream stream, String message) {
        try {
            stream.send("error", message);
        } catch (RuntimeException ignored) {
            // Connection may already be closed.
        } finally {
            stream.complete();
        }
    }

    private void triggerAsyncJudge(
        InterviewSession session,
        InterviewMessage userMessage,
        SseSessionStream stream
    ) {
        sseTaskExecutor.execute(() -> {
            UserContext.setCurrentUserId(session.getUserId());
            try {
                interviewJudgeService.judgeAndPersist(session, userMessage, false)
                    .ifPresent(result -> sendJudgeEvent(stream, result.json()));
                stream.complete();
            } catch (RuntimeException error) {
                log.error("Error in async judge task", error);
                stream.complete();
            } finally {
                UserContext.remove();
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
