package com.interview.interview.application;

import com.interview.shared.api.BusinessException;
import com.interview.interview.api.InterviewFinishResponse;
import com.interview.interview.domain.InterviewSession;
import com.interview.interview.application.port.InterviewSessionRepository;
import com.interview.platform.job.JobRequest;
import com.interview.platform.job.JobSchedulerPort;
import com.interview.platform.job.JobTicket;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class FinishInterview {

    private static final String STATUS_ONGOING = "ongoing";
    private static final String STATUS_GENERATING = "generating";
    private static final String STATUS_FINISHED = "finished";

    private final InterviewSessionAccess sessionAccess;
    private final InterviewSessionRepository interviewSessionRepository;
    private final JobSchedulerPort jobSchedulerPort;
    private final InterviewMessageService interviewMessageService;

    public InterviewFinishResponse execute(Long sessionId) {
        Long userId = sessionAccess.currentUserId();
        InterviewSession session = sessionAccess.requireOwned(sessionId, userId);
        String status = session.getStatus();

        if (STATUS_GENERATING.equals(status)) {
            return new InterviewFinishResponse(session.getId(), null, STATUS_GENERATING, null);
        }
        if (STATUS_FINISHED.equals(status)) {
            return new InterviewFinishResponse(session.getId(), session.getSummaryReport(), STATUS_FINISHED, null);
        }
        if (!STATUS_ONGOING.equals(status)) {
            throw BusinessException.badRequest("面试会话状态异常");
        }

        session.setStatus(STATUS_GENERATING);
        interviewSessionRepository.update(session);

        JobTicket job;
        try {
            job = jobSchedulerPort.enqueue(JobRequest.report(sessionId, userId));
            log.info("Scheduled report generation job {} for session {}", job.jobId(), sessionId);
        } catch (Exception exception) {
            restoreOngoingStatus(sessionId);
            throw BusinessException.badRequest("报告生成任务发布失败");
        }

        interviewMessageService.invalidateSessionLock(sessionId);
        return new InterviewFinishResponse(session.getId(), null, STATUS_GENERATING, job.jobId());
    }

    private void restoreOngoingStatus(Long sessionId) {
        try {
            InterviewSession restoreSession = interviewSessionRepository.selectById(sessionId);
            if (restoreSession != null && STATUS_GENERATING.equals(restoreSession.getStatus())) {
                restoreSession.setStatus(STATUS_ONGOING);
                interviewSessionRepository.update(restoreSession);
            }
        } catch (Exception restoreException) {
            log.error("Failed to restore session {} status to ongoing", sessionId, restoreException);
        }
    }
}
