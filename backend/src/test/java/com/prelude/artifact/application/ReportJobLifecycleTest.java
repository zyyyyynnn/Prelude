package com.prelude.artifact.application;

import com.prelude.activity.RealtimePort;
import com.prelude.interview.api.port.InterviewReportPort;
import com.prelude.interview.domain.InterviewSession;
import com.prelude.jobs.integration.BackgroundJobCancelled;
import com.prelude.jobs.integration.BackgroundJobFailed;
import com.prelude.jobs.integration.BackgroundJobSucceeded;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReportJobLifecycleTest {

    @Test
    void durableSuccessStaysSuccessfulWhenRealtimeDeliveryFails() {
        InterviewReportPort reportPort = mock(InterviewReportPort.class);
        RealtimePort realtime = mock(RealtimePort.class);
        InterviewSession session = new InterviewSession();
        session.setId(42L);
        session.setStatus("finished");
        session.setSummaryReport("{\"summary\":{}}");
        when(reportPort.findSession(42L)).thenReturn(session);
        doThrow(new RuntimeException("redis unavailable"))
            .when(realtime).publish(42L, "report_ready", session.getSummaryReport());
        ReportJobLifecycle lifecycle = new ReportJobLifecycle(reportPort, realtime);

        assertThatCode(() -> lifecycle.onSucceeded(
            new BackgroundJobSucceeded("job-1", "report.generate", 7L, 42L)))
            .doesNotThrowAnyException();

        verify(reportPort, never()).restoreOngoing(42L);
        verify(realtime).publish(42L, "report_ready", session.getSummaryReport());
    }

    @Test
    void terminalFailureRestoresAuthoritativeSessionBeforeBestEffortRealtime() {
        InterviewReportPort reportPort = mock(InterviewReportPort.class);
        RealtimePort realtime = mock(RealtimePort.class);
        doThrow(new RuntimeException("redis unavailable"))
            .when(realtime).publish(42L, "error", "报告生成失败，请稍后重试");
        ReportJobLifecycle lifecycle = new ReportJobLifecycle(reportPort, realtime);

        assertThatCode(() -> lifecycle.onFailed(
            new BackgroundJobFailed("job-2", "report.generate", 7L, 42L, "failed")))
            .doesNotThrowAnyException();

        InOrder order = inOrder(reportPort, realtime);
        order.verify(reportPort).restoreOngoing(42L);
        order.verify(realtime).publish(42L, "error", "报告生成失败，请稍后重试");
    }

    @Test
    void cancellationUsesTheSameAuthoritativeRestoreBoundary() {
        InterviewReportPort reportPort = mock(InterviewReportPort.class);
        RealtimePort realtime = mock(RealtimePort.class);
        ReportJobLifecycle lifecycle = new ReportJobLifecycle(reportPort, realtime);

        lifecycle.onCancelled(new BackgroundJobCancelled("job-3", "report.generate", 7L, 42L));

        InOrder order = inOrder(reportPort, realtime);
        order.verify(reportPort).restoreOngoing(42L);
        order.verify(realtime).publish(42L, "error", "报告生成已取消");
    }
}
