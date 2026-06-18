package com.interview.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.interview.common.BusinessException;
import com.interview.common.UserContext;
import com.interview.config.SseEmitterRegistry;
import com.interview.dto.InterviewChatRequest;
import com.interview.entity.InterviewMessage;
import com.interview.entity.InterviewSession;
import com.interview.llm.LlmRouter;
import com.interview.mapper.InterviewMessageMapper;
import com.interview.mapper.InterviewSessionMapper;
import com.interview.mapper.InterviewStageMapper;
import com.interview.mapper.PositionTemplateMapper;
import com.interview.mapper.ResumeMapper;
import com.interview.mapper.ScoreHistoryMapper;
import com.interview.mapper.UserWeaknessMapper;
import com.interview.service.DevFixtureService;
import com.interview.service.SessionRagService;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InterviewServiceImplChatListenTest {

    @Mock private ResumeMapper resumeMapper;
    @Mock private PositionTemplateMapper positionTemplateMapper;
    @Mock private InterviewSessionMapper interviewSessionMapper;
    @Mock private InterviewMessageMapper interviewMessageMapper;
    @Mock private InterviewStageMapper interviewStageMapper;
    @Mock private ScoreHistoryMapper scoreHistoryMapper;
    @Mock private UserWeaknessMapper userWeaknessMapper;
    @Mock private LlmRouter llmRouter;
    @Mock private DevFixtureService devFixtureService;
    @Mock private InterviewReportParser interviewReportParser;
    @Mock private SseEmitterRegistry sseEmitterRegistry;
    @Mock private StringRedisTemplate stringRedisTemplate;
    @Mock private SessionRagService sessionRagService;
    @Mock private RabbitTemplate rabbitTemplate;
    @Mock private InterviewStageManager interviewStageManager;
    @Mock private InterviewContextService interviewContextService;
    @Mock private InterviewJudgeService interviewJudgeService;
    @Mock private InterviewSummaryService interviewSummaryService;

    private final Executor directExecutor = Runnable::run;
    private InterviewServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new InterviewServiceImpl(
            resumeMapper,
            positionTemplateMapper,
            interviewSessionMapper,
            interviewMessageMapper,
            llmRouter,
            devFixtureService,
            interviewStageManager,
            interviewContextService,
            interviewJudgeService,
            interviewSummaryService,
            directExecutor,
            sessionRagService,
            sseEmitterRegistry,
            rabbitTemplate
        );
        UserContext.setCurrentUserId(42L);
    }

    @AfterEach
    void tearDown() {
        UserContext.remove();
    }

    @Test
    void listenRejectsSessionOwnedByAnotherUser() {
        InterviewSession session = ownedSession(7L, 99L);
        when(interviewSessionMapper.selectById(7L)).thenReturn(session);

        assertThatThrownBy(() -> service.listen(7L))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("无权访问");
    }

    @Test
    void listenRegistersOwnedSessionAndReturnsEmitter() {
        InterviewSession session = ownedSession(7L, 42L);
        when(interviewSessionMapper.selectById(7L)).thenReturn(session);

        SseEmitter emitter = service.listen(7L);

        assertThat(emitter).isNotNull();
        verify(sseEmitterRegistry).register(eq(7L), any(SseEmitter.class));
    }

    @Test
    void chatDeletesInsertedUserMessageAndClearsContextWhenLlmStreamingFailsBeforeAssistantPersisted() {
        InterviewSession session = ownedSession(7L, 42L);
        session.setStatus("ongoing");
        session.setLlmProvider("openai-compatible");
        session.setLlmModel("model-a");
        when(interviewSessionMapper.selectById(7L)).thenReturn(session);
        when(interviewMessageMapper.selectOne(any())).thenReturn(null);
        AtomicReference<InterviewMessage> inserted = new AtomicReference<>();
        doAnswer(invocation -> {
            InterviewMessage message = invocation.getArgument(0);
            message.setId(100L);
            inserted.set(message);
            return 1;
        }).when(interviewMessageMapper).insert(any(InterviewMessage.class));
        doThrow(new RuntimeException("llm down"))
            .when(llmRouter).streamWithSnapshot(any(), any(), any(), any());

        InterviewChatRequest request = new InterviewChatRequest();
        request.setContent("回答");

        service.chat(7L, request, false);

        verify(interviewMessageMapper).deleteById(100L);
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
