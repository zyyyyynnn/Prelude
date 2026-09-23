package com.prelude.artifact.application;

import com.prelude.activity.RealtimePort;
import com.prelude.interview.api.port.InterviewReportPort;
import com.prelude.interview.api.port.InterviewSessionSnapshot;
import com.prelude.interview.api.port.InterviewSessionStatus;
import com.prelude.jobs.integration.BackgroundJobCancelled;
import com.prelude.jobs.integration.BackgroundJobFailed;
import com.prelude.jobs.integration.BackgroundJobSucceeded;
import com.prelude.jobs.integration.JobTypes;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * Report-domain reaction to durable job terminal state. Job state remains the
 * execution truth; this listener converges report session state after commit,
 * while realtime delivery is notification-only and never changes the durable
 * outcome.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReportJobLifecycle {

    private static final String REPORT_JOB_TYPE = JobTypes.REPORT_GENERATE;

    private final InterviewReportPort interviewReportPort;
    private final RealtimePort realtimePort;

    @ApplicationModuleListener
    public void onSucceeded(BackgroundJobSucceeded event) {
        if (!REPORT_JOB_TYPE.equals(event.type())) {
            return;
        }
        InterviewSessionSnapshot session = interviewReportPort.findSession(event.subjectId());
        if (session == null || !InterviewSessionStatus.FINISHED.matches(session.status())
            || session.summaryReport() == null || session.summaryReport().isBlank()) {
            log.error("Report job {} succeeded without a durable finished report for session {}",
                event.jobId(), event.subjectId());
            return;
        }
        publishBestEffort(event.subjectId(), "report_ready", session.summaryReport());
    }

    @ApplicationModuleListener
    public void onFailed(BackgroundJobFailed event) {
        if (!REPORT_JOB_TYPE.equals(event.type())) {
            return;
        }
        interviewReportPort.restoreOngoing(event.subjectId());
        publishBestEffort(event.subjectId(), "error", "报告生成失败，请稍后重试");
    }

    @ApplicationModuleListener
    public void onCancelled(BackgroundJobCancelled event) {
        if (!REPORT_JOB_TYPE.equals(event.type())) {
            return;
        }
        interviewReportPort.restoreOngoing(event.subjectId());
        publishBestEffort(event.subjectId(), "error", "报告生成已取消");
    }

    private void publishBestEffort(Long sessionId, String event, String payload) {
        try {
            realtimePort.publish(sessionId, event, payload);
        } catch (RuntimeException exception) {
            log.warn("Realtime notification {} failed for session {}; durable state is unchanged",
                event, sessionId, exception);
        }
    }
}
