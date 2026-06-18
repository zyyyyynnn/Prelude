package com.interview.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.interview.entity.InterviewMessage;
import com.interview.entity.InterviewSession;
import com.interview.llm.LlmRouter;
import com.interview.mapper.InterviewMessageMapper;
import com.interview.mapper.InterviewSessionMapper;
import com.interview.service.VoiceService;
import com.interview.service.impl.InterviewContextService;
import com.interview.service.impl.InterviewJudgeService;
import com.interview.service.impl.InterviewMessageService;
import com.interview.service.impl.InterviewStageManager;
import com.interview.service.impl.InterviewSummaryService;
import com.interview.service.impl.VoiceInterviewSessionService;
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
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VoiceWebSocketHandlerTest {

    @Mock
    private VoiceService voiceService;
    @Mock
    private LlmRouter llmRouter;
    @Mock
    private InterviewSessionMapper interviewSessionMapper;
    @Mock
    private InterviewMessageMapper interviewMessageMapper;
    @Mock
    private InterviewStageManager interviewStageManager;
    @Mock
    private InterviewContextService interviewContextService;
    @Mock
    private InterviewJudgeService interviewJudgeService;
    @Mock
    private InterviewSummaryService interviewSummaryService;
    @Mock
    private WebSocketSession webSocketSession;

    private VoiceWebSocketHandler handler;
    private VoiceInterviewSessionService voiceInterviewSessionService;
    private InterviewMessageService interviewMessageService;

    @BeforeEach
    void setUp() {
        Executor directExecutor = Runnable::run;
        voiceInterviewSessionService = new VoiceInterviewSessionService(interviewSessionMapper);
        interviewMessageService = new InterviewMessageService(interviewMessageMapper);
        handler = new VoiceWebSocketHandler(
            voiceService,
            llmRouter,
            new ObjectMapper(),
            interviewStageManager,
            interviewContextService,
            interviewJudgeService,
            interviewSummaryService,
            voiceInterviewSessionService,
            interviewMessageService,
            directExecutor
        );
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("userId", 42L);
        when(webSocketSession.getAttributes()).thenReturn(attributes);
        when(webSocketSession.getId()).thenReturn("ws-1");
        when(webSocketSession.isOpen()).thenReturn(true);
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
        verifyNoInteractions(voiceService);
    }

    @Test
    void stopRevalidatesSessionBeforeAsyncVoiceProcessing() throws Exception {
        InterviewSession ongoing = session(7L, 42L, "ongoing");
        InterviewSession finished = session(7L, 42L, "finished");
        when(interviewSessionMapper.selectById(7L)).thenReturn(ongoing, finished);

        handler.afterConnectionEstablished(webSocketSession);
        handler.handleTextMessage(webSocketSession, new TextMessage("{\"type\":\"start\",\"sessionId\":7}"));
        handler.handleBinaryMessage(webSocketSession, new BinaryMessage(new byte[] {1, 2, 3}));
        handler.handleTextMessage(webSocketSession, new TextMessage("{\"type\":\"stop\"}"));

        ArgumentCaptor<TextMessage> messageCaptor = ArgumentCaptor.forClass(TextMessage.class);
        verify(webSocketSession).sendMessage(messageCaptor.capture());
        assertThat(messageCaptor.getValue().getPayload()).contains("面试会话不可用");
        verifyNoInteractions(voiceService);
    }

    @Test
    void stopUsesMaxSeqNumWhenPersistingVoiceUserMessage() throws Exception {
        InterviewSession ongoing = session(7L, 42L, "ongoing");
        when(interviewSessionMapper.selectById(7L)).thenReturn(ongoing, ongoing);
        when(voiceService.speechToText(eq(7L), any(byte[].class), eq("voice.webm"))).thenReturn("语音回答");
        when(interviewContextService.buildContextMessages(7L)).thenReturn(List.of());
        InterviewMessage latest = new InterviewMessage();
        latest.setSeqNum(5);
        when(interviewMessageMapper.selectOne(any())).thenReturn(latest);

        handler.afterConnectionEstablished(webSocketSession);
        handler.handleTextMessage(webSocketSession, new TextMessage("{\"type\":\"start\",\"sessionId\":7}"));
        handler.handleBinaryMessage(webSocketSession, new BinaryMessage(new byte[] {1, 2, 3}));
        handler.handleTextMessage(webSocketSession, new TextMessage("{\"type\":\"stop\"}"));

        ArgumentCaptor<InterviewMessage> messageCaptor = ArgumentCaptor.forClass(InterviewMessage.class);
        verify(interviewMessageMapper).insert(messageCaptor.capture());
        assertThat(messageCaptor.getValue().getRole()).isEqualTo("user");
        assertThat(messageCaptor.getValue().getSeqNum()).isEqualTo(6);
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
