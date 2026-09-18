package com.prelude.voice.web;

import com.prelude.identity.api.SessionValidity;
import com.prelude.test.SessionFixtures;
import com.prelude.voice.application.VoiceInterviewTurnService;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import tools.jackson.databind.ObjectMapper;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
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
    private final SessionValidity sessionValidity = mock(SessionValidity.class);

    @Test
    void aRevokedSessionClosesTheSocketWithAPolicyStatusInsteadOfProcessingFrames() throws Exception {
        when(sessionValidity.isActive(eq("auth-session-1"), eq(7L))).thenReturn(false);
        VoiceWebSocketHandler handler = new VoiceWebSocketHandler(
            objectMapper, turnService, sessionValidity);
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

    @Test
    void anActiveSessionProcessesBusinessFrames() throws Exception {
        when(sessionValidity.isActive(eq("auth-session-1"), eq(7L))).thenReturn(true);
        VoiceWebSocketHandler handler = new VoiceWebSocketHandler(
            objectMapper, turnService, sessionValidity);
        var socket = mock(org.springframework.web.socket.WebSocketSession.class);
        when(socket.getAttributes()).thenReturn(java.util.Map.of(
            "accountId", 7L, "authSessionId", "auth-session-1"));
        when(socket.getId()).thenReturn("conn-1");
        when(socket.isOpen()).thenReturn(true);
        when(turnService.validateActiveSession(7L, 51L))
            .thenReturn(SessionFixtures.create(51L));

        handler.handleTextMessage(socket,
            new TextMessage(objectMapper.writeValueAsString(java.util.Map.of("type", "start", "sessionId", 51))));

        verify(turnService, never()).processTurn(any(), any(), any(), any());
        verify(socket, never()).close(any(CloseStatus.class));
    }
}
