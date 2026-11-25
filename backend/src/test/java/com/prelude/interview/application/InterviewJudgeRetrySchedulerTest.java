package com.prelude.interview.application;

import com.prelude.interview.InterviewJudgeRetryProperties;
import com.prelude.interview.application.port.JudgeOutcome;
import com.prelude.interview.application.repository.InterviewMessageRepository;
import com.prelude.interview.application.repository.InterviewSessionRepository;
import com.prelude.interview.domain.InterviewMessage;
import com.prelude.interview.domain.InterviewSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InterviewJudgeRetrySchedulerTest {

    private final InterviewMessageRepository messages = mock(InterviewMessageRepository.class);
    private final InterviewSessionRepository sessions = mock(InterviewSessionRepository.class);
    private final InterviewJudgeService judge = mock(InterviewJudgeService.class);
    private final InterviewJudgeRetryProperties properties =
        new InterviewJudgeRetryProperties(true, Duration.ofMinutes(2), 25);
    private final InterviewJudgeRetryScheduler scheduler =
        new InterviewJudgeRetryScheduler(messages, sessions, judge, properties, 60_000L);
    private final InterviewSession session = new InterviewSession();
    private final InterviewMessage answer = new InterviewMessage();

    @BeforeEach
    void preparePendingAnswer() {
        session.setId(7L);
        session.setAccountId(3L);
        session.setModelExecutionSnapshotId(5L);
        answer.setId(11L);
        answer.setSessionId(7L);
        answer.setRole("user");
        answer.setCreatedAt(LocalDateTime.now().minusMinutes(10));
        when(sessions.selectById(7L)).thenReturn(session);
    }

    @Test
    void retrySchedulingCanBeDisabled() {
        new ApplicationContextRunner()
            .withBean(InterviewMessageRepository.class, () -> messages)
            .withBean(InterviewSessionRepository.class, () -> sessions)
            .withBean(InterviewJudgeService.class, () -> judge)
            .withBean(InterviewJudgeRetryProperties.class, () -> properties)
            .withUserConfiguration(InterviewJudgeRetryScheduler.class)
            .withPropertyValues("prelude.interview.judge.retry.enabled=false")
            .run(context -> assertThat(context).doesNotHaveBean(InterviewJudgeRetryScheduler.class));
    }

    @Test
    void retrySchedulingCreatesItsOwnWorkerWhenEnabled() {
        new ApplicationContextRunner()
            .withBean(InterviewMessageRepository.class, () -> messages)
            .withBean(InterviewSessionRepository.class, () -> sessions)
            .withBean(InterviewJudgeService.class, () -> judge)
            .withBean(InterviewJudgeRetryProperties.class, () -> properties)
            .withUserConfiguration(EnabledScheduling.class, InterviewJudgeRetryScheduler.class)
            .withPropertyValues(
                "prelude.interview.judge.retry.enabled=true",
                "prelude.interview.judge.retry.interval-ms=60000")
            .run(context -> assertThat(context).hasSingleBean(InterviewJudgeRetryScheduler.class));
    }

    @Test
    void retriesOnlyTheBoundedPendingBatchWithTheFrozenSessionSnapshot() {
        when(messages.findPendingForJudge(any(), eq(25))).thenReturn(List.of(answer));
        when(judge.judgeAndPersist(session, answer)).thenReturn(JudgeOutcome.completed(
            new com.prelude.interview.application.port.JudgeResult(8, "clear", "{\"score\":8}")));
        LocalDateTime before = LocalDateTime.now().minus(properties.pendingAge());

        scheduler.retryPendingAssessments();

        ArgumentCaptor<LocalDateTime> cutoff = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(messages).findPendingForJudge(cutoff.capture(), eq(25));
        assertThat(cutoff.getValue()).isBetween(before, LocalDateTime.now().minus(properties.pendingAge()));
        verify(judge).judgeAndPersist(session, answer);
        verify(sessions).selectById(7L);
    }

    @Test
    void busyLeavesTheAnswerPendingForALaterPass() {
        when(messages.findPendingForJudge(any(), eq(25))).thenReturn(List.of(answer));
        when(judge.judgeAndPersist(session, answer)).thenReturn(JudgeOutcome.busy());

        scheduler.retryPendingAssessments();

        verify(judge, times(1)).judgeAndPersist(session, answer);
        verify(messages, never()).updateAssessmentIfPending(any());
    }

    @Test
    void oneFailedRetryDoesNotStopTheRestOfTheBatch() {
        InterviewMessage second = new InterviewMessage();
        second.setId(12L);
        second.setSessionId(8L);
        InterviewSession secondSession = new InterviewSession();
        secondSession.setId(8L);
        when(messages.findPendingForJudge(any(), eq(25))).thenReturn(List.of(answer, second));
        when(sessions.selectById(8L)).thenReturn(secondSession);
        when(judge.judgeAndPersist(session, answer)).thenThrow(new IllegalStateException("provider unavailable"));
        when(judge.judgeAndPersist(secondSession, second)).thenReturn(JudgeOutcome.unscored("provider unavailable"));

        scheduler.retryPendingAssessments();

        verify(judge).judgeAndPersist(session, answer);
        verify(judge).judgeAndPersist(secondSession, second);
    }

    @Test
    void aDeletedSessionDoesNotBlockLaterPendingAnswers() {
        InterviewMessage second = new InterviewMessage();
        second.setId(12L);
        second.setSessionId(8L);
        InterviewSession secondSession = new InterviewSession();
        secondSession.setId(8L);
        when(messages.findPendingForJudge(any(), eq(25))).thenReturn(List.of(answer, second));
        when(sessions.selectById(7L)).thenReturn(null);
        when(sessions.selectById(8L)).thenReturn(secondSession);

        scheduler.retryPendingAssessments();

        verify(judge, never()).judgeAndPersist(any(), eq(answer));
        verify(judge).judgeAndPersist(secondSession, second);
    }

    @Configuration(proxyBeanMethods = false)
    @EnableScheduling
    static class EnabledScheduling {
    }
}
