package com.interview.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.interview.common.BusinessException;
import com.interview.common.UserContext;
import com.interview.config.RabbitMqConfig;
import com.interview.config.SseEmitterRegistry;
import com.interview.dto.InterviewFinishResponse;
import com.interview.entity.InterviewSession;
import com.interview.llm.LlmRouter;
import com.interview.mapper.InterviewMessageMapper;
import com.interview.mapper.InterviewSessionMapper;
import com.interview.mapper.InterviewStageMapper;
import com.interview.mapper.PositionTemplateMapper;
import com.interview.mapper.ResumeMapper;
import com.interview.mapper.ScoreHistoryMapper;
import com.interview.mapper.UserWeaknessMapper;
import com.interview.messaging.ReportJobMessage;
import com.interview.service.DevFixtureService;
import com.interview.service.SessionRagService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InterviewServiceImplFinishTest {

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

    private final Executor directExecutor = Runnable::run;

    private InterviewServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new InterviewServiceImpl(
            resumeMapper,
            positionTemplateMapper,
            interviewSessionMapper,
            interviewMessageMapper,
            interviewStageMapper,
            scoreHistoryMapper,
            userWeaknessMapper,
            llmRouter,
            devFixtureService,
            new ObjectMapper(),
            interviewReportParser,
            directExecutor,
            stringRedisTemplate,
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
    void finishPublishesJobToRabbitAndReturnsGenerating() {
        InterviewSession session = new InterviewSession();
        session.setId(7L);
        session.setUserId(42L);
        session.setStatus("ongoing");
        when(interviewSessionMapper.selectById(7L)).thenReturn(session);

        InterviewFinishResponse response = service.finish(7L);

        assertThat(response.getSessionId()).isEqualTo(7L);
        assertThat(response.getStatus()).isEqualTo("generating");
        assertThat(response.getJobId()).isNotBlank();
        assertThat(response.getSummaryReport()).isNull();

        ArgumentCaptor<ReportJobMessage> jobCaptor = ArgumentCaptor.forClass(ReportJobMessage.class);
        verify(rabbitTemplate).convertAndSend(
            eq(RabbitMqConfig.REPORT_EXCHANGE),
            eq(RabbitMqConfig.REPORT_ROUTING_KEY),
            jobCaptor.capture()
        );
        ReportJobMessage published = jobCaptor.getValue();
        assertThat(published.sessionId()).isEqualTo(7L);
        assertThat(published.userId()).isEqualTo(42L);
        assertThat(published.jobId()).isEqualTo(response.getJobId());

        verify(interviewSessionMapper, times(1)).updateById(any(InterviewSession.class));
    }

    @Test
    void finishRestoresSessionToOngoingWhenRabbitPublishFails() {
        InterviewSession ongoing = new InterviewSession();
        ongoing.setId(7L);
        ongoing.setUserId(42L);
        ongoing.setStatus("ongoing");

        InterviewSession generating = new InterviewSession();
        generating.setId(7L);
        generating.setUserId(42L);
        generating.setStatus("generating");

        when(interviewSessionMapper.selectById(7L))
            .thenReturn(ongoing)
            .thenReturn(generating);
        doThrow(new AmqpException("broker down"))
            .when(rabbitTemplate).convertAndSend(
                anyString(), anyString(), any(ReportJobMessage.class)
            );

        assertThatThrownBy(() -> service.finish(7L))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("报告生成任务发布失败");

        ArgumentCaptor<InterviewSession> sessionCaptor = ArgumentCaptor.forClass(InterviewSession.class);
        verify(interviewSessionMapper, times(2)).updateById(sessionCaptor.capture());
        InterviewSession restored = sessionCaptor.getAllValues().get(1);
        assertThat(restored.getStatus()).isEqualTo("ongoing");
    }

    @Test
    void finishLeavesGeneratingStateIntactWhenRabbitPublishSucceeds() {
        InterviewSession session = new InterviewSession();
        session.setId(7L);
        session.setUserId(42L);
        session.setStatus("ongoing");
        when(interviewSessionMapper.selectById(7L)).thenReturn(session);

        service.finish(7L);

        ArgumentCaptor<InterviewSession> sessionCaptor = ArgumentCaptor.forClass(InterviewSession.class);
        verify(interviewSessionMapper, times(1)).updateById(sessionCaptor.capture());
        assertThat(sessionCaptor.getValue().getStatus()).isEqualTo("generating");
    }

    @Test
    void finishDoesNotTouchSseEmitterRegistry() {
        InterviewSession session = new InterviewSession();
        session.setId(7L);
        session.setUserId(42L);
        session.setStatus("ongoing");
        when(interviewSessionMapper.selectById(7L)).thenReturn(session);

        service.finish(7L);

        verify(sseEmitterRegistry, never()).broadcast(any(), anyString(), anyString());
    }
}
