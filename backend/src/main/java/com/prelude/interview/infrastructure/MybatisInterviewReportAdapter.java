package com.prelude.interview.infrastructure;

import com.prelude.interview.api.port.InterviewMessageSnapshot;
import com.prelude.interview.api.port.InterviewReportPort;
import com.prelude.interview.api.port.InterviewSessionSnapshot;
import com.prelude.interview.api.port.InterviewStageSnapshot;
import com.prelude.interview.application.InterviewStageManager;
import com.prelude.interview.application.repository.InterviewMessageRepository;
import com.prelude.interview.application.repository.InterviewSessionRepository;
import com.prelude.interview.application.repository.InterviewStageRepository;
import com.prelude.interview.domain.InterviewMessage;
import com.prelude.interview.domain.InterviewSession;
import com.prelude.interview.domain.InterviewStage;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Serves the report pipeline from the interview domain. The port speaks in snapshots;
 * this adapter is the single place that turns a domain object into one, so a new column
 * only has to be added here.
 */
@Component
@RequiredArgsConstructor
public class MybatisInterviewReportAdapter implements InterviewReportPort {

    private final InterviewSessionRepository interviewSessionRepository;
    private final InterviewMessageRepository interviewMessageRepository;
    private final InterviewStageRepository interviewStageRepository;
    private final InterviewStageManager interviewStageManager;

    @Override
    public InterviewSessionSnapshot findSession(Long sessionId) {
        InterviewSession session = interviewSessionRepository.selectById(sessionId);
        return session == null ? null : new InterviewSessionSnapshot(
            session.getId(),
            session.getAccountId(),
            session.getTargetPosition(),
            session.getModelExecutionSnapshotId(),
            session.getStatus(),
            session.getSummary(),
            session.getSummaryReport()
        );
    }

    @Override
    public List<InterviewMessageSnapshot> listMessages(Long sessionId) {
        return interviewMessageRepository.listBySession(sessionId).stream()
            .map(this::toSnapshot)
            .toList();
    }

    @Override
    public void closeCurrentStage(Long sessionId) {
        interviewStageManager.closeCurrentStage(sessionId);
    }

    @Override
    public List<InterviewStageSnapshot> listStages(Long sessionId) {
        return interviewStageRepository.listBySession(sessionId).stream()
            .map(this::toSnapshot)
            .toList();
    }

    @Override
    public boolean completeReport(Long sessionId, String reportJson) {
        return interviewSessionRepository.completeReportIfGenerating(sessionId, reportJson) == 1;
    }

    @Override
    public void restoreOngoing(Long sessionId) {
        interviewSessionRepository.restoreOngoingIfGenerating(sessionId);
    }

    private InterviewMessageSnapshot toSnapshot(InterviewMessage message) {
        return new InterviewMessageSnapshot(
            message.getId(),
            message.getSessionId(),
            message.getRole(),
            message.getContent(),
            message.getSeqNum(),
            message.getScore(),
            message.getHint(),
            message.getCreatedAt()
        );
    }

    private InterviewStageSnapshot toSnapshot(InterviewStage stage) {
        return new InterviewStageSnapshot(
            stage.getId(),
            stage.getSessionId(),
            stage.getStageName(),
            stage.getStartedAt(),
            stage.getEndedAt()
        );
    }
}
