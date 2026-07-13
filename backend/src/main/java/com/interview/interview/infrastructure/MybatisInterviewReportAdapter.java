package com.interview.interview.infrastructure;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.interview.interview.domain.InterviewMessage;
import com.interview.interview.domain.InterviewSession;
import com.interview.interview.domain.InterviewStage;
import com.interview.interview.api.port.InterviewReportPort;
import com.interview.interview.infrastructure.persistence.InterviewMessageMapper;
import com.interview.interview.infrastructure.persistence.InterviewSessionMapper;
import com.interview.interview.infrastructure.persistence.InterviewStageMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
public class MybatisInterviewReportAdapter implements InterviewReportPort {

    private static final String STATUS_GENERATING = "generating";
    private static final String STATUS_ONGOING = "ongoing";
    private static final String STATUS_FINISHED = "finished";

    private final InterviewSessionMapper interviewSessionMapper;
    private final InterviewMessageMapper interviewMessageMapper;
    private final InterviewStageMapper interviewStageMapper;

    @Override
    public InterviewSession findSession(Long sessionId) {
        return interviewSessionMapper.selectById(sessionId);
    }

    @Override
    public List<InterviewMessage> listMessages(Long sessionId) {
        return interviewMessageMapper.selectList(new LambdaQueryWrapper<InterviewMessage>()
            .eq(InterviewMessage::getSessionId, sessionId)
            .orderByAsc(InterviewMessage::getSeqNum));
    }

    @Override
    public void closeCurrentStage(Long sessionId) {
        InterviewStage stage = interviewStageMapper.selectOne(new LambdaQueryWrapper<InterviewStage>()
            .eq(InterviewStage::getSessionId, sessionId)
            .isNull(InterviewStage::getEndedAt)
            .orderByDesc(InterviewStage::getStartedAt)
            .last("LIMIT 1"));
        if (stage != null) {
            stage.setEndedAt(LocalDateTime.now());
            interviewStageMapper.updateById(stage);
        }
    }

    @Override
    public List<InterviewStage> listStages(Long sessionId) {
        return interviewStageMapper.selectList(new LambdaQueryWrapper<InterviewStage>()
            .eq(InterviewStage::getSessionId, sessionId)
            .orderByAsc(InterviewStage::getStartedAt)
            .orderByAsc(InterviewStage::getId));
    }

    @Override
    public void completeReport(Long sessionId, String reportJson) {
        InterviewSession session = interviewSessionMapper.selectById(sessionId);
        if (session != null && STATUS_GENERATING.equals(session.getStatus())) {
            session.setStatus(STATUS_FINISHED);
            session.setSummaryReport(reportJson);
            interviewSessionMapper.updateById(session);
        }
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
