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
import com.interview.service.DemoModeService;
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
    @Mock private DemoModeService demoModeService;
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
            demoModeService,
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
        when(demoModeService.isEnabled()).thenReturn(true);
        when(demoModeService.resolveReport("Backend Engineer"))
            .thenReturn("{\"reportMarkdown\":\"# Report\",\"scores\":{\"technical\":8,\"expression\":7,\"logic\":9}}");
        when(interviewReportParser.parse(anyString()))
            .thenReturn(new InterviewReportParser.ParsedReport("# Report", 8, 7, 9));
        when(demoModeService.buildWeaknesses(42L, 7L)).thenReturn(Collections.<UserWeakness>emptyList());

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
        when(demoModeService.isEnabled()).thenReturn(true);
        when(demoModeService.resolveReport("Backend Engineer"))
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
        verify(demoModeService, never()).isEnabled();
        verify(demoModeService, never()).resolveReport(anyString());
    }
}
