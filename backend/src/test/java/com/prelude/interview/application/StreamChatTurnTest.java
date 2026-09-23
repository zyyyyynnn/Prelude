package com.prelude.interview.application;

import com.prelude.activity.RealtimeConnection;
import com.prelude.activity.SseSessionStream;
import com.prelude.interview.application.port.InterviewTurnPort;
import com.prelude.test.SessionFixtures;
import org.junit.jupiter.api.Test;

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
    private final SseSessionStream[] opened = new SseSessionStream[1];
    private RealtimeConnection connection;

    private StreamChatTurn streamChatTurn(boolean sessionActive) {
        return SessionFixtures.createStreamChatTurn(
            sessionAccess,
            interviewTurnPort,
            mock(RealtimeConnection.class),
            sessionActive,
            "auth-session-1",
            7L
        );
    }

    @Test
    void aRevokedSessionStopsTheStreamAtTheNextSendBoundary() {
        StreamChatTurn streamChatTurn = streamChatTurn(false);
        connection = SessionFixtures.openStream(SessionFixtures.mockRealtimePort(), 51L, opened);
        when(interviewTurnPort.execute(any(), any())).thenAnswer(invocation -> {
            SessionFixtures.sendDelta(invocation, "authenticated business delta");
            return null;
        });

        streamChatTurn.execute(51L, "回答", false, "auth-session-1", opened[0]);

        SessionFixtures.verifyConnectionNeverSends(connection, "message");
        SessionFixtures.verifyConnectionSend(connection, "error", "登录已失效，请重新登录");
        SessionFixtures.verifyConnectionComplete(connection);
    }

    @Test
    void anActiveSessionKeepsStreamingBusinessData() {
        StreamChatTurn streamChatTurn = streamChatTurn(true);
        connection = SessionFixtures.openStream(SessionFixtures.mockRealtimePort(), 51L, opened);
        when(interviewTurnPort.execute(any(), any())).thenAnswer(invocation -> {
            SessionFixtures.sendDelta(invocation, "business delta");
            return SessionFixtures.turnResult(
                SessionFixtures.turnSession(51L),
                SessionFixtures.userTurn(12L, 51L, "回答"),
                "business delta");
        });
        when(interviewTurnPort.judgeAndPersist(any(), any())).thenReturn(java.util.Optional.empty());

        streamChatTurn.execute(51L, "回答", false, "auth-session-1", opened[0]);

        SessionFixtures.verifyConnectionSend(connection, "message", "business delta");
        verify(interviewTurnPort).summarizeIfNeeded(any());
    }
}
