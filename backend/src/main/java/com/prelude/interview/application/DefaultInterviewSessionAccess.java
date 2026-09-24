package com.prelude.interview.application;

import com.prelude.BusinessException;
import com.prelude.identity.api.CurrentAccount;
import com.prelude.interview.api.port.InterviewSessionStatus;
import com.prelude.interview.application.repository.InterviewSessionRepository;
import com.prelude.interview.domain.InterviewSession;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DefaultInterviewSessionAccess implements InterviewSessionAccess {

    private final InterviewSessionRepository interviewSessionRepository;
    private final CurrentAccount currentAccount;

    @Override
    public long currentAccountId() {
        return currentAccount.requireId();
    }

    @Override
    public InterviewSession requireOwned(Long sessionId, long accountId) {
        InterviewSession session = interviewSessionRepository.selectById(sessionId);
        if (session == null || accountId != session.getAccountId()) {
            throw BusinessException.badRequest("面试会话不存在或无权访问");
        }
        return session;
    }

    @Override
    public InterviewSession requireOngoing(Long sessionId, long accountId) {
        InterviewSession session = requireOwned(sessionId, accountId);
        if (!InterviewSessionStatus.ONGOING.matches(session.getStatus())) {
            throw BusinessException.badRequest("面试会话已结束");
        }
        return session;
    }
}
