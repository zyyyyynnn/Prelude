package com.prelude.interview.application;

import com.prelude.interview.application.port.InterviewTurnPort;
import com.prelude.test.SessionFixtures;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Long-connection revocation: an SSE stream whose originating Spring Session
 * is revoked stops emitting authenticated business data at the next send
 * boundary instead of continuing the turn.
 */
class StreamChatTurnTest {

    private final InterviewSessionAccess sessionAccess = mock(InterviewSessionAccess.class);
    private final InterviewTurnPort interviewTurnPort = SessionFixtures.mockTurnPort();
    private final Object connection = SessionFixtures.mockRealtimeConnection();

    private StreamChatTurn streamChatTurn(boolean sessionActive) {
        return SessionFixtures.createStreamChatTurn(
            sessionAccess,
            interviewTurnPort,
            connection,
            sessionActive,
            "auth-session-1",
            7L
        );
    }

    @Test
    void aRevokedSessionStopsTheStreamAtTheNextSendBoundary() {
        StreamChatTurn streamChatTurn = streamChatTurn(false);
        when(interviewTurnPort.execute(any(), any())).thenAnswer(invocation -> {
            SessionFixtures.sendDelta(invocation, "authenticated business delta");
            return null;
        });

        SseEmitter emitter = streamChatTurn.execute(51L, "回答", false, "auth-session-1");

        assertThat(emitter).isNotNull();
        SessionFixtures.verifyConnectionSend(connection, "error", "登录已失效，请重新登录");
        SessionFixtures.verifyConnectionComplete(connection);
    }

    @Test
    void anActiveSessionKeepsStreamingBusinessData() {
        StreamChatTurn streamChatTurn = streamChatTurn(true);
        when(interviewTurnPort.execute(any(), any())).thenAnswer(invocation -> {
            SessionFixtures.sendDelta(invocation, "business delta");
            return SessionFixtures.turnResult(
                SessionFixtures.create(51L),
                SessionFixtures.message(),
                "business delta");
        });
        when(interviewTurnPort.judgeAndPersist(any(), any())).thenReturn(java.util.Optional.empty());

        streamChatTurn.execute(51L, "回答", false, "auth-session-1");

        SessionFixtures.verifyConnectionSend(connection, "message", "business delta");
        verify(interviewTurnPort).summarizeIfNeeded(any());
    }
}