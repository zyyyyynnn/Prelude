package com.prelude.interview.application;

import com.prelude.BusinessException;
import com.prelude.interview.application.repository.InterviewMessageRepository;
import com.prelude.interview.application.repository.InterviewSessionRepository;
import com.prelude.interview.application.repository.InterviewStageRepository;
import com.prelude.interview.domain.AssessmentStatus;
import com.prelude.interview.domain.InterviewMessage;
import com.prelude.interview.domain.InterviewStage;
import com.prelude.interview.integration.InterviewSessionStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class InterviewMessageService {

    private final InterviewMessageRepository interviewMessageRepository;
    private final InterviewSessionRepository interviewSessionRepository;
    private final InterviewStageRepository interviewStageRepository;

    @Transactional(rollbackFor = Exception.class)
    public InterviewMessage insertUserMessage(Long sessionId, String content) {
        lockOngoing(sessionId);
        setAwaitingAnswer(sessionId, false);
        return append(sessionId, "user", content, AssessmentStatus.PENDING);
    }

    @Transactional(rollbackFor = Exception.class)
    public InterviewMessage insertAssistantMessage(Long sessionId, String content) {
        lockOngoing(sessionId);
        setAwaitingAnswer(sessionId, true);
        return append(sessionId, "assistant", content, null);
    }

    @Transactional(rollbackFor = Exception.class)
    public InterviewMessage insertSystemMessage(Long sessionId, String content) {
        lockOngoing(sessionId);
        return append(sessionId, "system", content, null);
    }

    private void lockOngoing(Long sessionId) {
        InterviewSessionStatus status = interviewSessionRepository.lockAppendOrder(sessionId);
        if (status == null) {
            throw BusinessException.badRequest("面试会话不存在");
        }
        if (status != InterviewSessionStatus.ONGOING) {
            throw BusinessException.badRequest("面试会话已结束或正在生成报告");
        }
    }

    private void setAwaitingAnswer(Long sessionId, boolean awaitingAnswer) {
        InterviewStage stage = interviewStageRepository.findCurrentForUpdate(sessionId);
        if (stage == null) {
            throw BusinessException.badRequest("面试阶段不存在");
        }
        stage.setAwaitingAnswer(awaitingAnswer);
        interviewStageRepository.update(stage);
    }

    private InterviewMessage append(
        Long sessionId, String role, String content, AssessmentStatus assessmentStatus
    ) {
        InterviewMessage message = new InterviewMessage();
        message.setSessionId(sessionId);
        message.setRole(role);
        message.setContent(content);
        message.setAssessmentStatus(assessmentStatus);
        message.setSeqNum(nextSeqNum(sessionId));
        interviewMessageRepository.add(message);
        return message;
    }

    private int nextSeqNum(Long sessionId) {
        InterviewMessage latest = interviewMessageRepository.findLatestForAppend(sessionId);
        Integer seqNum = latest == null ? null : latest.getSeqNum();
        return seqNum == null ? 0 : seqNum + 1;
    }
}
