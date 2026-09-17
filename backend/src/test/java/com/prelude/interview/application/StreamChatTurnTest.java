package com.prelude.interview.application;

import com.prelude.activity.RealtimeConnection;
import com.prelude.activity.RealtimePort;
import com.prelude.identity.api.SessionValidity;
import com.prelude.interview.application.port.InterviewTurnPort;
import com.prelude.interview.application.port.InterviewTurnResult;
import com.prelude.interview.application.port.InterviewTurnSink;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
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
    private final InterviewTurnPort interviewTurnPort = mock(InterviewTurnPort.class);
    private final RealtimePort realtimePort = mock(RealtimePort.class);
    private final RealtimeConnection connection = mock(RealtimeConnection.class);
    private final SessionValidity sessionValidity = mock(SessionValidity.class);

    private StreamChatTurn streamChatTurn(boolean sessionActive) {
        when(sessionValidity.isActive(eq("auth-session-1"), eq(7L))).thenReturn(sessionActive);
        when(realtimePort.register(any(), anyString(), any())).thenReturn(connection);
        when(sessionAccess.currentAccountId()).thenReturn(7L);
        return new StreamChatTurn(
            sessionAccess,
            interviewTurnPort,
            Runnable::run,
            realtimePort,
            sessionValidity
        );
    }

    @Test
    void aRevokedSessionStopsTheStreamAtTheNextSendBoundary() {
        StreamChatTurn streamChatTurn = streamChatTurn(false);
        AtomicReference<InterviewTurnSink> sink = new AtomicReference<>();
        when(interviewTurnPort.execute(any(), any())).thenAnswer(invocation -> {
            sink.set(invocation.getArgument(1));
            sink.get().assistantDelta("authenticated business delta");
            return null;
        });

        SseEmitter emitter = streamChatTurn.execute(51L, "回答", false, "auth-session-1");

        assertThat(emitter).isNotNull();
        verify(connection).send("error", "登录已失效，请重新登录");
        verify(connection).complete();
    }

    @Test
    void anActiveSessionKeepsStreamingBusinessData() {
        StreamChatTurn streamChatTurn = streamChatTurn(true);
        when(interviewTurnPort.execute(any(), any())).thenAnswer(invocation -> {
            InterviewTurnSink sink = invocation.getArgument(1);
            sink.assistantDelta("business delta");
            return new InterviewTurnResult(
                mock(com.prelude.interview.domain.InterviewSession.class),
                mock(com.prelude.interview.domain.InterviewMessage.class),
                "business delta");
        });
        when(interviewTurnPort.judgeAndPersist(any(), any())).thenReturn(java.util.Optional.empty());

        streamChatTurn.execute(51L, "回答", false, "auth-session-1");

        verify(connection).send("message", "business delta");
        verify(interviewTurnPort).summarizeIfNeeded(any());
    }
}
