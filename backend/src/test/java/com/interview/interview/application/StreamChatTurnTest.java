package com.interview.interview.application;

import com.interview.interview.application.InterviewContextService;
import com.interview.interview.application.InterviewJudgeService;
import com.interview.interview.application.InterviewMessageService;
import com.interview.interview.application.InterviewStageManager;
import com.interview.interview.application.InterviewSummaryService;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.interview.shared.api.BusinessException;
import com.interview.shared.web.UserContext;
import com.interview.platform.realtime.RealtimeConnection;
import com.interview.platform.realtime.RealtimePort;
import com.interview.platform.realtime.SessionStreamSink;
import com.interview.interview.api.InterviewChatRequest;
import com.interview.interview.domain.InterviewMessage;
import com.interview.interview.domain.InterviewSession;
import com.interview.interview.application.FinishInterview;
import com.interview.interview.application.InterviewSessionAccess;
import com.interview.interview.application.InterviewSessionQueryService;
import com.interview.interview.application.ListenInterview;
import com.interview.interview.application.RunInterviewTurn;
import com.interview.interview.application.StartInterview;
import com.interview.interview.application.StreamChatTurn;
import com.interview.interview.application.UpdateInterviewStage;
import com.interview.platform.llm.ChatPort;
import com.interview.interview.infrastructure.persistence.InterviewMessageMapper;
import com.interview.interview.infrastructure.persistence.InterviewSessionMapper;
import com.interview.interview.infrastructure.persistence.InterviewStageMapper;
import com.interview.catalog.infrastructure.persistence.PositionTemplateMapper;
import com.interview.resume.infrastructure.persistence.ResumeMapper;
import com.interview.insight.infrastructure.persistence.ScoreHistoryMapper;
import com.interview.insight.infrastructure.persistence.UserWeaknessMapper;
import com.interview.insight.infrastructure.InterviewReportParser;
import com.interview.interview.application.port.InterviewFixturePort;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StreamChatTurnTest {

    @Mock private StartInterview startInterview;
    @Mock private PositionTemplateMapper positionTemplateMapper;
    @Mock private InterviewSessionMapper interviewSessionMapper;
    @Mock private InterviewMessageMapper interviewMessageMapper;
    @Mock private InterviewStageMapper interviewStageMapper;
    @Mock private ScoreHistoryMapper scoreHistoryMapper;
    @Mock private UserWeaknessMapper userWeaknessMapper;
    @Mock private ChatPort chatPort;
    @Mock private InterviewFixturePort devFixtureService;
    @Mock private InterviewReportParser interviewReportParser;
    @Mock private RealtimePort sseEmitterRegistry;
    @Mock private RealtimeConnection realtimeConnection;
    @Mock private StringRedisTemplate stringRedisTemplate;
    @Mock private RabbitTemplate rabbitTemplate;
    @Mock private InterviewStageManager interviewStageManager;
    @Mock private InterviewContextService interviewContextService;
    @Mock private InterviewJudgeService interviewJudgeService;
    @Mock private InterviewSummaryService interviewSummaryService;
    private final Executor directExecutor = Runnable::run;
    private StreamChatTurn streamChatTurn;
    private ListenInterview listenInterview;

    @BeforeEach
    void setUp() {
        InterviewSessionAccess sessionAccess = new InterviewSessionAccess(interviewSessionMapper);
        InterviewMessageService messageService = new InterviewMessageService(interviewMessageMapper);
        RunInterviewTurn runInterviewTurn = new RunInterviewTurn(
            sessionAccess,
            interviewMessageMapper,
            chatPort,
            devFixtureService,
            interviewStageManager,
            interviewContextService,
            messageService
        );
        streamChatTurn = new StreamChatTurn(
            sessionAccess,
            runInterviewTurn,
            interviewJudgeService,
            interviewSummaryService,
            directExecutor,
            sseEmitterRegistry
        );
        listenInterview = new ListenInterview(sessionAccess, sseEmitterRegistry);
        UserContext.setCurrentUserId(42L);
        lenient().when(sseEmitterRegistry.register(anyLong(), anyString(), any(SessionStreamSink.class)))
            .thenReturn(realtimeConnection);
    }

    @AfterEach
    void tearDown() {
        UserContext.remove();
    }

    @Test
    void listenRejectsSessionOwnedByAnotherUser() {
        InterviewSession session = ownedSession(7L, 99L);
        when(interviewSessionMapper.selectById(7L)).thenReturn(session);

        assertThatThrownBy(() -> listenInterview.execute(7L))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("无权访问");
    }

    @Test
    void listenRegistersOwnedSessionAndReturnsEmitter() {
        InterviewSession session = ownedSession(7L, 42L);
        when(interviewSessionMapper.selectById(7L)).thenReturn(session);

        SseEmitter emitter = listenInterview.execute(7L);

        assertThat(emitter).isNotNull();
        verify(sseEmitterRegistry).register(eq(7L), anyString(), any(SessionStreamSink.class));
    }

    @Test
    void chatDeletesInsertedUserMessageAndClearsContextWhenLlmStreamingFailsBeforeAssistantPersisted() {
        InterviewSession session = ownedSession(7L, 42L);
        session.setStatus("ongoing");
        session.setLlmProvider("openai-compatible");
        session.setLlmModel("model-a");
        when(interviewSessionMapper.selectById(7L)).thenReturn(session);
        when(interviewMessageMapper.findLatest(7L)).thenReturn(null);
        AtomicReference<InterviewMessage> inserted = new AtomicReference<>();
        doAnswer(invocation -> {
            InterviewMessage message = invocation.getArgument(0);
            message.setId(100L);
            inserted.set(message);
            return 1;
        }).when(interviewMessageMapper).add(any(InterviewMessage.class));
        doThrow(new RuntimeException("llm down"))
            .when(chatPort).stream(any(), any());

        InterviewChatRequest request = new InterviewChatRequest();
        request.setContent("回答");

        streamChatTurn.execute(7L, request, false);

        verify(interviewMessageMapper).delete(100L);
        assertThat(UserContext.getCurrentUserId()).isNull();
        assertThat(UserContext.getCurrentSessionId()).isNull();
    }

    private InterviewSession ownedSession(Long sessionId, Long userId) {
        InterviewSession session = new InterviewSession();
        session.setId(sessionId);
        session.setUserId(userId);
        return session;
    }
}
