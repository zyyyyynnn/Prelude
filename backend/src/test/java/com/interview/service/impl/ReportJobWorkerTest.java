package com.interview.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.interview.common.UserContext;
import com.interview.config.SseEmitterRegistry;
import com.interview.entity.InterviewMessage;
import com.interview.entity.InterviewSession;
import com.interview.entity.InterviewStage;
import com.interview.entity.ScoreHistory;
import com.interview.entity.UserWeakness;
import com.interview.llm.LlmRouter;
import com.interview.mapper.InterviewMessageMapper;
import com.interview.mapper.InterviewSessionMapper;
import com.interview.mapper.InterviewStageMapper;
import com.interview.mapper.ScoreHistoryMapper;
import com.interview.mapper.UserWeaknessMapper;
import com.interview.messaging.ReportJobMessage;
import com.interview.service.DevFixtureService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReportJobWorkerTest {

    @Mock private InterviewSessionMapper interviewSessionMapper;
    @Mock private InterviewMessageMapper interviewMessageMapper;
    @Mock private InterviewStageMapper interviewStageMapper;
    @Mock private ScoreHistoryMapper scoreHistoryMapper;
    @Mock private UserWeaknessMapper userWeaknessMapper;
    @Mock private LlmRouter llmRouter;
    @Mock private DevFixtureService devFixtureService;
    @Mock private InterviewReportParser interviewReportParser;
    @Mock private SseEmitterRegistry sseEmitterRegistry;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private ReportJobWorker worker;

    @BeforeEach
    void setUp() {
        worker = new ReportJobWorker(
            objectMapper,
            interviewSessionMapper,
            interviewMessageMapper,
            interviewStageMapper,
            scoreHistoryMapper,
            userWeaknessMapper,
            llmRouter,
            devFixtureService,
            interviewReportParser,
            sseEmitterRegistry
        );
    }

    @AfterEach
    void tearDown() {
        UserContext.remove();
    }

    @Test
    void handleReportJobFinishesSessionAndBroadcastsReportReady() {
        ReportJobMessage job = new ReportJobMessage(7L, 42L, "job-1");

        InterviewSession session = new InterviewSession();
        session.setId(7L);
        session.setUserId(42L);
        session.setStatus("generating");
        session.setTargetPosition("Backend Engineer");

        InterviewStage stage = new InterviewStage();
        stage.setId(99L);
        stage.setSessionId(7L);

        when(interviewSessionMapper.selectById(7L)).thenReturn(session);
        when(interviewMessageMapper.selectList(any(LambdaQueryWrapper.class)))
            .thenReturn(Collections.<InterviewMessage>emptyList());
        when(interviewStageMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(stage);
        when(devFixtureService.isEnabled()).thenReturn(true);
        when(devFixtureService.resolveReport("Backend Engineer"))
            .thenReturn("{\"reportMarkdown\":\"# Report\",\"scores\":{\"technical\":8,\"expression\":7,\"logic\":9}}");
        when(interviewReportParser.parse(anyString()))
            .thenReturn(new InterviewReportParser.ParsedReport("# Report", 8, 7, 9));
        when(devFixtureService.buildWeaknesses(42L, 7L)).thenReturn(Collections.<UserWeakness>emptyList());

        worker.handleReportJob(job);

        ArgumentCaptor<InterviewSession> sessionCaptor = ArgumentCaptor.forClass(InterviewSession.class);
        verify(interviewSessionMapper, times(1)).updateById(sessionCaptor.capture());
        InterviewSession persisted = sessionCaptor.getValue();
        assertThat(persisted.getStatus()).isEqualTo("finished");
        assertThat(persisted.getSummaryReport()).isEqualTo("# Report");

        ArgumentCaptor<InterviewStage> stageCaptor = ArgumentCaptor.forClass(InterviewStage.class);
        verify(interviewStageMapper).updateById(stageCaptor.capture());
        assertThat(stageCaptor.getValue().getEndedAt()).isNotNull();

        verify(scoreHistoryMapper, times(1)).insert(any(ScoreHistory.class));
        verify(sseEmitterRegistry).broadcast(eq(7L), eq("report_ready"), eq("# Report"));
        verify(sseEmitterRegistry, never()).broadcast(anyLong(), eq("error"), anyString());
    }

    @Test
    void handleReportJobDeletesThenInsertsScoreHistory() {
        ReportJobMessage job = new ReportJobMessage(8L, 42L, "job-score");

        InterviewSession session = new InterviewSession();
        session.setId(8L);
        session.setUserId(42L);
        session.setStatus("generating");
        session.setTargetPosition("Backend Engineer");

        InterviewStage stage = new InterviewStage();
        stage.setId(101L);
        stage.setSessionId(8L);

        when(interviewSessionMapper.selectById(8L)).thenReturn(session);
        when(interviewMessageMapper.selectList(any(LambdaQueryWrapper.class)))
            .thenReturn(Collections.<InterviewMessage>emptyList());
        when(interviewStageMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(stage);
        when(devFixtureService.isEnabled()).thenReturn(true);
        when(devFixtureService.resolveReport("Backend Engineer"))
            .thenReturn("{\"reportMarkdown\":\"# R\",\"scores\":{\"technical\":7,\"expression\":6,\"logic\":8}}");
        when(interviewReportParser.parse(anyString()))
            .thenReturn(new InterviewReportParser.ParsedReport("# R", 7, 6, 8));
        when(devFixtureService.buildWeaknesses(42L, 8L)).thenReturn(Collections.<UserWeakness>emptyList());

        worker.handleReportJob(job);

        verify(scoreHistoryMapper, times(1)).delete(any(LambdaQueryWrapper.class));
        ArgumentCaptor<ScoreHistory> scoreCaptor = ArgumentCaptor.forClass(ScoreHistory.class);
        verify(scoreHistoryMapper, times(1)).insert(scoreCaptor.capture());
        ScoreHistory score = scoreCaptor.getValue();
        assertThat(score.getUserId()).isEqualTo(42L);
        assertThat(score.getSessionId()).isEqualTo(8L);
        assertThat(score.getTechnicalScore()).isEqualTo(7);
        assertThat(score.getExpressionScore()).isEqualTo(6);
        assertThat(score.getLogicScore()).isEqualTo(8);
    }

    @Test
    void handleReportJobDeletesThenInsertsWeaknesses() {
        ReportJobMessage job = new ReportJobMessage(9L, 42L, "job-weakness");

        InterviewSession session = new InterviewSession();
        session.setId(9L);
        session.setUserId(42L);
        session.setStatus("generating");
        session.setTargetPosition("Backend Engineer");

        InterviewStage stage = new InterviewStage();
        stage.setId(102L);
        stage.setSessionId(9L);

        UserWeakness weakness = new UserWeakness();
        weakness.setUserId(42L);
        weakness.setSessionId(9L);
        weakness.setCategory("性能量化");
        weakness.setDescription("回答需要补充指标口径和压测数据。");

        when(interviewSessionMapper.selectById(9L)).thenReturn(session);
        when(interviewMessageMapper.selectList(any(LambdaQueryWrapper.class)))
            .thenReturn(Collections.<InterviewMessage>emptyList());
        when(interviewStageMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(stage);
        when(devFixtureService.isEnabled()).thenReturn(true);
        when(devFixtureService.resolveReport("Backend Engineer"))
            .thenReturn("{\"reportMarkdown\":\"# R\",\"scores\":{\"technical\":7,\"expression\":6,\"logic\":8}}");
        when(interviewReportParser.parse(anyString()))
            .thenReturn(new InterviewReportParser.ParsedReport("# R", 7, 6, 8));
        when(devFixtureService.buildWeaknesses(42L, 9L)).thenReturn(List.of(weakness));

        worker.handleReportJob(job);

        verify(userWeaknessMapper, times(1)).delete(any(LambdaQueryWrapper.class));
        ArgumentCaptor<UserWeakness> weaknessCaptor = ArgumentCaptor.forClass(UserWeakness.class);
        verify(userWeaknessMapper, times(1)).insert(weaknessCaptor.capture());
        UserWeakness inserted = weaknessCaptor.getValue();
        assertThat(inserted.getUserId()).isEqualTo(42L);
        assertThat(inserted.getSessionId()).isEqualTo(9L);
        assertThat(inserted.getCategory()).isEqualTo("性能量化");
        assertThat(inserted.getDescription()).isEqualTo("回答需要补充指标口径和压测数据。");
        verify(sseEmitterRegistry, atLeastOnce()).broadcast(eq(9L), eq("report_ready"), anyString());
    }

    @Test
    void handleReportJobClearsUserContextInFinallyBlock() {
        ReportJobMessage job = new ReportJobMessage(10L, 42L, "job-cleanup");

        InterviewSession session = new InterviewSession();
        session.setId(10L);
        session.setUserId(42L);
        session.setStatus("generating");
        session.setTargetPosition("Backend Engineer");

        InterviewStage stage = new InterviewStage();
        stage.setId(103L);
        stage.setSessionId(10L);

        when(interviewSessionMapper.selectById(10L)).thenReturn(session);
        when(interviewMessageMapper.selectList(any(LambdaQueryWrapper.class)))
            .thenReturn(Collections.<InterviewMessage>emptyList());
        when(interviewStageMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(stage);
        when(devFixtureService.isEnabled()).thenReturn(true);
        when(devFixtureService.resolveReport("Backend Engineer"))
            .thenReturn("{\"reportMarkdown\":\"# R\",\"scores\":{\"technical\":7,\"expression\":6,\"logic\":8}}");
        when(interviewReportParser.parse(anyString()))
            .thenReturn(new InterviewReportParser.ParsedReport("# R", 7, 6, 8));
        when(devFixtureService.buildWeaknesses(42L, 10L)).thenReturn(Collections.<UserWeakness>emptyList());

        worker.handleReportJob(job);

        org.assertj.core.api.Assertions.assertThat(UserContext.getCurrentUserId()).isNull();
        org.assertj.core.api.Assertions.assertThat(UserContext.getCurrentSessionId()).isNull();
    }

    @Test
    void handleReportJobRestoresOngoingAndBroadcastsErrorOnFailure() {
        ReportJobMessage job = new ReportJobMessage(7L, 42L, "job-2");

        InterviewSession generating = new InterviewSession();
        generating.setId(7L);
        generating.setUserId(42L);
        generating.setStatus("generating");
        generating.setTargetPosition("Backend Engineer");

        InterviewSession sameRowAfterFailure = new InterviewSession();
        sameRowAfterFailure.setId(7L);
        sameRowAfterFailure.setUserId(42L);
        sameRowAfterFailure.setStatus("generating");
        sameRowAfterFailure.setTargetPosition("Backend Engineer");

        when(interviewSessionMapper.selectById(7L))
            .thenReturn(generating)
            .thenReturn(sameRowAfterFailure);
        when(interviewMessageMapper.selectList(any(LambdaQueryWrapper.class)))
            .thenReturn(Collections.<InterviewMessage>emptyList());
        when(devFixtureService.isEnabled()).thenReturn(true);
        when(devFixtureService.resolveReport("Backend Engineer"))
            .thenThrow(new RuntimeException("LLM broker down"));

        worker.handleReportJob(job);

        ArgumentCaptor<InterviewSession> sessionCaptor = ArgumentCaptor.forClass(InterviewSession.class);
        verify(interviewSessionMapper, times(1)).updateById(sessionCaptor.capture());
        InterviewSession restored = sessionCaptor.getValue();
        assertThat(restored.getStatus()).isEqualTo("ongoing");

        verify(sseEmitterRegistry).broadcast(eq(7L), eq("error"), anyString());
    }

    @Test
    void handleReportJobSkipsWhenSessionMissing() {
        ReportJobMessage job = new ReportJobMessage(404L, 42L, "job-3");
        when(interviewSessionMapper.selectById(404L)).thenReturn(null);

        worker.handleReportJob(job);

        verify(interviewSessionMapper, never()).updateById(any(InterviewSession.class));
        verify(sseEmitterRegistry, never()).broadcast(anyLong(), anyString(), anyString());
    }

    @Test
    void handleReportJobSkipsWhenSessionAlreadyFinished() {
        ReportJobMessage job = new ReportJobMessage(7L, 42L, "job-4");

        InterviewSession finishedSession = new InterviewSession();
        finishedSession.setId(7L);
        finishedSession.setUserId(42L);
        finishedSession.setStatus("finished");
        finishedSession.setTargetPosition("Backend Engineer");

        when(interviewSessionMapper.selectById(7L)).thenReturn(finishedSession);

        worker.handleReportJob(job);

        verify(interviewMessageMapper, never()).selectList(any(LambdaQueryWrapper.class));
        verify(llmRouter, never()).chatWithSnapshot(anyString(), anyString(), anyList());
        verify(interviewReportParser, never()).parse(anyString());
        verify(interviewSessionMapper, never()).updateById(any(InterviewSession.class));
        verify(sseEmitterRegistry, never()).broadcast(anyLong(), eq("report_ready"), anyString());
        verify(sseEmitterRegistry, never()).broadcast(anyLong(), eq("error"), anyString());
        verify(devFixtureService, never()).isEnabled();
        verify(devFixtureService, never()).resolveReport(anyString());
    }

    @Test
    void handleReportJobSkipsWhenSessionOngoing() {
        ReportJobMessage job = new ReportJobMessage(11L, 42L, "job-ongoing");

        InterviewSession ongoingSession = new InterviewSession();
        ongoingSession.setId(11L);
        ongoingSession.setUserId(42L);
        ongoingSession.setStatus("ongoing");
        ongoingSession.setTargetPosition("Backend Engineer");

        when(interviewSessionMapper.selectById(11L)).thenReturn(ongoingSession);

        worker.handleReportJob(job);

        verify(interviewMessageMapper, never()).selectList(any(LambdaQueryWrapper.class));
        verify(llmRouter, never()).chatWithSnapshot(anyString(), anyString(), anyList());
        verify(interviewReportParser, never()).parse(anyString());
        verify(scoreHistoryMapper, never()).delete(any(LambdaQueryWrapper.class));
        verify(scoreHistoryMapper, never()).insert(any(ScoreHistory.class));
        verify(userWeaknessMapper, never()).delete(any(LambdaQueryWrapper.class));
        verify(userWeaknessMapper, never()).insert(any(UserWeakness.class));
        verify(interviewSessionMapper, never()).updateById(any(InterviewSession.class));
        verify(sseEmitterRegistry, never()).broadcast(anyLong(), anyString(), anyString());
        verify(devFixtureService, never()).isEnabled();
    }

    @Test
    void handleReportJobClearsUserContextEvenWhenSessionMissing() {
        ReportJobMessage job = new ReportJobMessage(404L, 42L, "job-cleanup-missing");
        when(interviewSessionMapper.selectById(404L)).thenReturn(null);

        worker.handleReportJob(job);

        org.assertj.core.api.Assertions.assertThat(UserContext.getCurrentUserId()).isNull();
        org.assertj.core.api.Assertions.assertThat(UserContext.getCurrentSessionId()).isNull();
    }
}
