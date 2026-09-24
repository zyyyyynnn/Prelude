package com.prelude.voice.web;

import com.prelude.identity.api.SessionValidity;
import com.prelude.interview.application.port.InterviewSessionGuard;
import com.prelude.voice.application.VoiceInterviewTurnService;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import tools.jackson.databind.ObjectMapper;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Long-connection revocation for the voice WebSocket: frames on a connection
 * whose originating Spring Session is revoked close the socket with a policy
 * status instead of processing authenticated audio.
 */
class VoiceWebSocketHandlerSessionRevocationTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final VoiceInterviewTurnService turnService = mock(VoiceInterviewTurnService.class);
    private final InterviewSessionGuard interviewSessionGuard = mock(InterviewSessionGuard.class);
    private final SessionValidity sessionValidity = mock(SessionValidity.class);

    @Test
    void aRevokedSessionClosesTheSocketWithAPolicyStatusInsteadOfProcessingFrames() throws Exception {
        when(sessionValidity.isActive(eq("auth-session-1"), eq(7L))).thenReturn(false);
        VoiceWebSocketHandler handler = new VoiceWebSocketHandler(
            objectMapper, turnService, interviewSessionGuard, sessionValidity);
        var socket = mock(org.springframework.web.socket.WebSocketSession.class);
        when(socket.getAttributes()).thenReturn(java.util.Map.of(
            "accountId", 7L, "authSessionId", "auth-session-1"));
        when(socket.getId()).thenReturn("conn-1");
        when(socket.isOpen()).thenReturn(true);

        handler.handleTextMessage(socket,
            new TextMessage(objectMapper.writeValueAsString(java.util.Map.of("type", "start", "sessionId", 51))));

        verify(socket).close(CloseStatus.POLICY_VIOLATION.withReason("session revoked"));
        verify(turnService, never()).processTurn(any(), any(), any(), any());
    }

    /* A `start` frame registers the session; it is the `stop` frame that carries audio into
       `processTurn`. So the honest judgement here is that a live session is accepted silently —
       no close and no error frame back — rather than that anything was processed. */
    @Test
    void anActiveSessionIsAcceptedWithoutClosingTheSocketOrReportingAnError() throws Exception {
        when(sessionValidity.isActive(eq("auth-session-1"), eq(7L))).thenReturn(true);
        when(interviewSessionGuard.isOngoing(7L, 51L)).thenReturn(true);
        VoiceWebSocketHandler handler = new VoiceWebSocketHandler(
            objectMapper, turnService, interviewSessionGuard, sessionValidity);
        var socket = mock(org.springframework.web.socket.WebSocketSession.class);
        when(socket.getAttributes()).thenReturn(java.util.Map.of(
            "accountId", 7L, "authSessionId", "auth-session-1"));
        when(socket.getId()).thenReturn("conn-1");
        when(socket.isOpen()).thenReturn(true);

        handler.handleTextMessage(socket,
            new TextMessage(objectMapper.writeValueAsString(java.util.Map.of("type", "start", "sessionId", 51))));

        verify(socket, never()).close(any(CloseStatus.class));
        verify(socket, never()).sendMessage(any());
    }

    /* The counter-case the previous version only implied: a session that passes the auth check
       but is no longer ongoing must still be refused, and refused with a message. */
    @Test
    void aSessionThatIsNoLongerOngoingIsRefusedWithAnErrorFrame() throws Exception {
        when(sessionValidity.isActive(eq("auth-session-1"), eq(7L))).thenReturn(true);
        when(interviewSessionGuard.isOngoing(7L, 51L)).thenReturn(false);
        VoiceWebSocketHandler handler = new VoiceWebSocketHandler(
            objectMapper, turnService, interviewSessionGuard, sessionValidity);
        var socket = mock(org.springframework.web.socket.WebSocketSession.class);
        when(socket.getAttributes()).thenReturn(java.util.Map.of(
            "accountId", 7L, "authSessionId", "auth-session-1"));
        when(socket.getId()).thenReturn("conn-1");
        when(socket.isOpen()).thenReturn(true);

        handler.handleTextMessage(socket,
            new TextMessage(objectMapper.writeValueAsString(java.util.Map.of("type", "start", "sessionId", 51))));

        verify(socket).sendMessage(any());
        verify(socket, never()).close(any(CloseStatus.class));
    }
}
