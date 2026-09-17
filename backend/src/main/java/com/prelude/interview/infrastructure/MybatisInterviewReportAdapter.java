package com.prelude.interview.infrastructure;

import com.prelude.interview.domain.InterviewMessage;
import com.prelude.interview.domain.InterviewSession;
import com.prelude.interview.domain.InterviewStage;
import com.prelude.interview.api.port.InterviewReportPort;
import com.prelude.interview.application.InterviewStageManager;
import com.prelude.interview.application.port.InterviewMessageRepository;
import com.prelude.interview.application.port.InterviewStageRepository;
import com.prelude.interview.infrastructure.persistence.InterviewSessionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class MybatisInterviewReportAdapter implements InterviewReportPort {

    private static final String STATUS_GENERATING = "generating";
    private static final String STATUS_ONGOING = "ongoing";

    private final InterviewSessionMapper interviewSessionMapper;
    private final InterviewMessageRepository interviewMessageRepository;
    private final InterviewStageRepository interviewStageRepository;
    private final InterviewStageManager interviewStageManager;

    @Override
    public InterviewSession findSession(Long sessionId) {
        return interviewSessionMapper.selectById(sessionId);
    }

    @Override
    public List<InterviewMessage> listMessages(Long sessionId) {
        return interviewMessageRepository.listBySession(sessionId);
    }

    @Override
    public void closeCurrentStage(Long sessionId) {
        interviewStageManager.closeCurrentStage(sessionId);
    }

    @Override
    public List<InterviewStage> listStages(Long sessionId) {
        return interviewStageRepository.listBySession(sessionId);
    }

    @Override
    public boolean completeReport(Long sessionId, String reportJson) {
        return interviewSessionMapper.completeReportIfGenerating(sessionId, reportJson) == 1;
    }

    @Override
    public void restoreOngoing(Long sessionId) {
        InterviewSession session = interviewSessionMapper.selectById(sessionId);
        if (session != null && STATUS_GENERATING.equals(session.getStatus())) {
            session.setStatus(STATUS_ONGOING);
            interviewSessionMapper.updateById(session);
        }
    }
}
