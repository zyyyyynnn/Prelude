package com.interview.interview.api.voice;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.interview.interview.domain.InterviewSession;
import com.interview.interview.infrastructure.persistence.InterviewSessionMapper;
import com.interview.interview.api.voice.VoiceInterviewSessionService;
import com.interview.interview.api.voice.VoiceInterviewTurnService;
import com.interview.interview.api.voice.VoiceTurnEventSink;
import com.interview.interview.api.voice.VoiceWebSocketHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VoiceWebSocketHandlerTest {

    @Mock
    private InterviewSessionMapper interviewSessionMapper;
    @Mock
    private VoiceInterviewTurnService voiceInterviewTurnService;
    @Mock
    private WebSocketSession webSocketSession;

    private VoiceWebSocketHandler handler;
    private VoiceInterviewSessionService voiceInterviewSessionService;

    @BeforeEach
    void setUp() {
        voiceInterviewSessionService = new VoiceInterviewSessionService(interviewSessionMapper);
        handler = new VoiceWebSocketHandler(
            new ObjectMapper(),
            voiceInterviewSessionService,
            voiceInterviewTurnService
        );
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("userId", 42L);
        when(webSocketSession.getAttributes()).thenReturn(attributes);
        when(webSocketSession.getId()).thenReturn("ws-1");
        org.mockito.Mockito.lenient().when(webSocketSession.isOpen()).thenReturn(true);
    }

    @Test
    void startRejectsSessionOwnedByOtherUserAndDoesNotInitializeActiveSession() throws Exception {
        InterviewSession foreignSession = session(7L, 99L, "ongoing");
        when(interviewSessionMapper.selectById(7L)).thenReturn(foreignSession);

        handler.afterConnectionEstablished(webSocketSession);
        handler.handleTextMessage(webSocketSession, new TextMessage("{\"type\":\"start\",\"sessionId\":7}"));
        handler.handleTextMessage(webSocketSession, new TextMessage("{\"type\":\"stop\"}"));

        ArgumentCaptor<TextMessage> messageCaptor = ArgumentCaptor.forClass(TextMessage.class);
        verify(webSocketSession, times(2)).sendMessage(messageCaptor.capture());
        assertThat(messageCaptor.getAllValues().get(0).getPayload()).contains("面试会话不可用");
        assertThat(messageCaptor.getAllValues().get(1).getPayload()).contains("面试会话未初始化");
        verifyNoInteractions(voiceInterviewTurnService);
    }

    @Test
    void stopDelegatesAudioBufferToTurnServiceAfterValidStart() throws Exception {
        InterviewSession ongoing = session(7L, 42L, "ongoing");
        when(interviewSessionMapper.selectById(7L)).thenReturn(ongoing);

        handler.afterConnectionEstablished(webSocketSession);
        handler.handleTextMessage(webSocketSession, new TextMessage("{\"type\":\"start\",\"sessionId\":7}"));
        handler.handleBinaryMessage(webSocketSession, new BinaryMessage(new byte[] {1, 2, 3}));
        handler.handleTextMessage(webSocketSession, new TextMessage("{\"type\":\"stop\"}"));

        ArgumentCaptor<byte[]> bytesCaptor = ArgumentCaptor.forClass(byte[].class);
        verify(voiceInterviewTurnService).processTurn(
            org.mockito.ArgumentMatchers.eq(42L),
            org.mockito.ArgumentMatchers.eq(7L),
            bytesCaptor.capture(),
            any(VoiceTurnEventSink.class)
        );
        assertThat(bytesCaptor.getValue()).containsExactly(1, 2, 3);
    }

    @Test
    void stopWithEmptyAudioBufferDoesNotInvokeTurnService() throws Exception {
        InterviewSession ongoing = session(7L, 42L, "ongoing");
        when(interviewSessionMapper.selectById(7L)).thenReturn(ongoing);

        handler.afterConnectionEstablished(webSocketSession);
        handler.handleTextMessage(webSocketSession, new TextMessage("{\"type\":\"start\",\"sessionId\":7}"));
        handler.handleTextMessage(webSocketSession, new TextMessage("{\"type\":\"stop\"}"));

        ArgumentCaptor<TextMessage> messageCaptor = ArgumentCaptor.forClass(TextMessage.class);
        verify(webSocketSession).sendMessage(messageCaptor.capture());
        assertThat(messageCaptor.getValue().getPayload()).contains("没有检测到任何音频数据");
        verify(voiceInterviewTurnService, never()).processTurn(anyLong(), anyLong(), any(byte[].class), any());
    }

    private InterviewSession session(Long id, Long userId, String status) {
        InterviewSession session = new InterviewSession();
        session.setId(id);
        session.setUserId(userId);
        session.setStatus(status);
        session.setLlmProvider("deepseek");
        session.setLlmModel("deepseek-chat");
        return session;
    }
}
