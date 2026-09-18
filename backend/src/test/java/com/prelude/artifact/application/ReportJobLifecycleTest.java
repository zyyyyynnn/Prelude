package com.prelude.artifact.application;

import com.prelude.test.JobFixtures;
import com.prelude.test.SessionFixtures;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReportJobLifecycleTest {

    @Test
    void durableSuccessStaysSuccessfulWhenRealtimeDeliveryFails() {
        var reportPort = SessionFixtures.mockReportPort();
        var realtime = SessionFixtures.mockRealtimePort();
        var session = SessionFixtures.create(42L, "finished", "{\"summary\":{}}");
        when(reportPort.findSession(42L)).thenReturn(session);
        doThrow(new RuntimeException("redis unavailable"))
            .when(realtime).publish(42L, "report_ready", session.getSummaryReport());
        ReportJobLifecycle lifecycle = new ReportJobLifecycle(reportPort, realtime);

        assertThatCode(() -> lifecycle.onSucceeded(
            JobFixtures.succeeded("job-1", "report.generate", 7L, 42L)))
            .doesNotThrowAnyException();

        verify(reportPort, never()).restoreOngoing(42L);
        verify(realtime).publish(42L, "report_ready", session.getSummaryReport());
    }

    @Test
    void terminalFailureRestoresAuthoritativeSessionBeforeBestEffortRealtime() {
        var reportPort = SessionFixtures.mockReportPort();
        var realtime = SessionFixtures.mockRealtimePort();
        doThrow(new RuntimeException("redis unavailable"))
            .when(realtime).publish(42L, "error", "报告生成失败，请稍后重试");
        ReportJobLifecycle lifecycle = new ReportJobLifecycle(reportPort, realtime);

        assertThatCode(() -> lifecycle.onFailed(
            JobFixtures.failed("job-2", "report.generate", 7L, 42L, "failed")))
            .doesNotThrowAnyException();

        InOrder order = inOrder(reportPort, realtime);
        order.verify(reportPort).restoreOngoing(42L);
        order.verify(realtime).publish(42L, "error", "报告生成失败，请稍后重试");
    }

    @Test
    void cancellationUsesTheSameAuthoritativeRestoreBoundary() {
        var reportPort = SessionFixtures.mockReportPort();
        var realtime = SessionFixtures.mockRealtimePort();
        ReportJobLifecycle lifecycle = new ReportJobLifecycle(reportPort, realtime);

        lifecycle.onCancelled(JobFixtures.cancelled("job-3", "report.generate", 7L, 42L));

        InOrder order = inOrder(reportPort, realtime);
        order.verify(reportPort).restoreOngoing(42L);
        order.verify(realtime).publish(42L, "error", "报告生成已取消");
    }
}
