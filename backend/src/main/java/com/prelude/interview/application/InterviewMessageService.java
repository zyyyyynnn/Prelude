package com.prelude.interview.application;

import com.prelude.BusinessException;
import com.prelude.interview.application.repository.InterviewMessageRepository;
import com.prelude.interview.application.repository.InterviewSessionRepository;
import com.prelude.interview.domain.InterviewMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Appends messages to a session, numbering them from 0 within that session. */
@Service
@RequiredArgsConstructor
public class InterviewMessageService {

    private static final String STATUS_ONGOING = "ongoing";

    private final InterviewMessageRepository interviewMessageRepository;
    private final InterviewSessionRepository interviewSessionRepository;

    /**
     * The number is allocated under the session row's write lock, so whoever appends next reads
     * what the previous append committed rather than racing it, and the unique key on
     * {@code (session_id, seq_num)} refuses the alternative outloud. The same lock carries the
     * status gate: a turn that lost the ongoing→generating race to {@code FinishInterview} is
     * refused here instead of appending into a closed session.
     */
    @Transactional(rollbackFor = Exception.class)
    public InterviewMessage insertMessage(Long sessionId, String role, String content) {
        String status = interviewSessionRepository.lockAppendOrder(sessionId);
        if (status == null) {
            throw BusinessException.badRequest("面试会话不存在");
        }
        if (!STATUS_ONGOING.equals(status)) {
            throw BusinessException.badRequest("面试会话已结束或正在生成报告");
        }

        InterviewMessage message = new InterviewMessage();
        message.setSessionId(sessionId);
        message.setRole(role);
        message.setContent(content);
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
