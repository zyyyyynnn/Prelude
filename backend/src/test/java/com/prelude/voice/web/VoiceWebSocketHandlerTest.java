package com.prelude.voice.web;

import com.prelude.identity.api.SessionValidity;
import com.prelude.interview.application.port.InterviewSessionGuard;
import com.prelude.voice.application.VoiceInterviewTurnService;
import com.prelude.voice.application.VoiceTurnEventSink;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import tools.jackson.databind.ObjectMapper;

import java.nio.ByteBuffer;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class VoiceWebSocketHandlerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final VoiceInterviewTurnService turnService = mock(VoiceInterviewTurnService.class);
    private final InterviewSessionGuard interviewSessionGuard = mock(InterviewSessionGuard.class);
    private final SessionValidity sessionValidity = mock(SessionValidity.class);
    private final VoiceWebSocketHandler handler = new VoiceWebSocketHandler(
        objectMapper, turnService, interviewSessionGuard, sessionValidity);

    @Test
    void aRevokedSessionClosesTheSocketWithAPolicyStatusInsteadOfProcessingFrames() throws Exception {
        when(sessionValidity.isActive(eq("auth-session-1"), eq(7L))).thenReturn(false);
        WebSocketSession socket = socket();

        text(socket, Map.of("type", "start", "sessionId", 51));

        verify(socket).close(CloseStatus.POLICY_VIOLATION.withReason("session revoked"));
        verify(turnService, never()).processTurn(any(), any(), any(), any());
    }

    @Test
    void anActiveSessionIsAcceptedWithoutClosingTheSocketOrReportingAnError() throws Exception {
        activeSocket();
        WebSocketSession socket = socket();
        handler.afterConnectionEstablished(socket);
        when(interviewSessionGuard.isOngoing(7L, 51L)).thenReturn(true);

        text(socket, Map.of("type", "start", "sessionId", 51));

        verify(socket, never()).close(any(CloseStatus.class));
        verify(socket, never()).sendMessage(any());
    }

    @Test
    void aSessionThatIsNoLongerOngoingIsRefusedWithAnErrorFrame() throws Exception {
        activeSocket();
        WebSocketSession socket = socket();
        handler.afterConnectionEstablished(socket);

        text(socket, Map.of("type", "start", "sessionId", 51));

        verify(socket).sendMessage(any());
        verify(socket, never()).close(any(CloseStatus.class));
    }

    @Test
    void processingRejectsANewStartAndTheOldGenerationCannotWriteIntoTheNextSession() throws Exception {
        activeSocket();
        WebSocketSession socket = socket();
        handler.afterConnectionEstablished(socket);
        when(interviewSessionGuard.isOngoing(7L, 51L)).thenReturn(true);
        when(interviewSessionGuard.isOngoing(7L, 52L)).thenReturn(true);
        text(socket, Map.of("type", "start", "sessionId", 51));
        handler.handleBinaryMessage(socket, new BinaryMessage(ByteBuffer.wrap(new byte[] {1})));
        text(socket, Map.of("type", "stop"));
        ArgumentCaptor<VoiceTurnEventSink> sinks = ArgumentCaptor.forClass(VoiceTurnEventSink.class);
        verify(turnService).processTurn(eq(7L), eq(51L), any(), sinks.capture());
        VoiceTurnEventSink oldSink = sinks.getValue();

        oldSink.assistantText("old");
        verify(socket, times(1)).sendMessage(any());
        text(socket, Map.of("type", "start", "sessionId", 52));
        verify(socket, times(2)).sendMessage(any());
        verify(interviewSessionGuard, never()).isOngoing(7L, 52L);

        oldSink.turnFinished();
        text(socket, Map.of("type", "start", "sessionId", 52));
        oldSink.assistantText("stale");
        oldSink.audio("stale");
        verify(socket, times(2)).sendMessage(any());
        verify(interviewSessionGuard).isOngoing(7L, 52L);

        handler.handleBinaryMessage(socket, new BinaryMessage(ByteBuffer.wrap(new byte[] {2})));
        text(socket, Map.of("type", "stop"));
        verify(turnService).processTurn(eq(7L), eq(52L), any(), any());
        verify(turnService, times(1)).processTurn(eq(7L), eq(51L), any(), any());
    }

    @Test
    void aLateCallbackCannotWriteIntoTheNextTurnOfTheSameSession() throws Exception {
        activeSocket();
        WebSocketSession socket = socket();
        handler.afterConnectionEstablished(socket);
        when(interviewSessionGuard.isOngoing(7L, 51L)).thenReturn(true);
        text(socket, Map.of("type", "start", "sessionId", 51));
        handler.handleBinaryMessage(socket, new BinaryMessage(ByteBuffer.wrap(new byte[] {1})));
        text(socket, Map.of("type", "stop"));
        ArgumentCaptor<VoiceTurnEventSink> sinks = ArgumentCaptor.forClass(VoiceTurnEventSink.class);
        verify(turnService).processTurn(eq(7L), eq(51L), any(), sinks.capture());
        VoiceTurnEventSink previous = sinks.getValue();
        previous.turnFinished();

        text(socket, Map.of("type", "start", "sessionId", 51));
        handler.handleBinaryMessage(socket, new BinaryMessage(ByteBuffer.wrap(new byte[] {2})));
        text(socket, Map.of("type", "stop"));
        verify(turnService, times(2)).processTurn(eq(7L), eq(51L), any(), sinks.capture());
        VoiceTurnEventSink current = sinks.getAllValues().getLast();

        previous.assistantText("late");
        verify(socket, never()).sendMessage(any());
        current.assistantText("current");
        verify(socket).sendMessage(any());
    }

    @Test
    void closingTheConnectionInvalidatesEverySinkGeneration() throws Exception {
        activeSocket();
        WebSocketSession socket = socket();
        handler.afterConnectionEstablished(socket);
        when(interviewSessionGuard.isOngoing(7L, 51L)).thenReturn(true);
        text(socket, Map.of("type", "start", "sessionId", 51));
        handler.handleBinaryMessage(socket, new BinaryMessage(ByteBuffer.wrap(new byte[] {1})));
        text(socket, Map.of("type", "stop"));
        ArgumentCaptor<VoiceTurnEventSink> sinks = ArgumentCaptor.forClass(VoiceTurnEventSink.class);
        verify(turnService).processTurn(eq(7L), eq(51L), any(), sinks.capture());

        handler.afterConnectionClosed(socket, CloseStatus.NORMAL);
        sinks.getValue().status("speech_end");
        sinks.getValue().error("late");

        verify(socket, never()).sendMessage(any());
        assertThat(sinks.getValue().isCurrent()).isFalse();
    }

    private void activeSocket() {
        when(sessionValidity.isActive(eq("auth-session-1"), eq(7L))).thenReturn(true);
    }

    private WebSocketSession socket() {
        WebSocketSession socket = mock(WebSocketSession.class);
        when(socket.getAttributes()).thenReturn(Map.of(
            "accountId", 7L, "authSessionId", "auth-session-1"));
        when(socket.getId()).thenReturn("conn-1");
        when(socket.isOpen()).thenReturn(true);
        return socket;
    }

    private void text(WebSocketSession socket, Map<String, Object> payload) throws Exception {
        handler.handleTextMessage(socket, new TextMessage(objectMapper.writeValueAsString(payload)));
    }
}
