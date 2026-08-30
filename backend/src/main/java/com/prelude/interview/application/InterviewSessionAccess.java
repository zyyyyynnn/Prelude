package com.prelude.interview.application;

import com.prelude.BusinessException;
import com.prelude.identity.api.CurrentAccount;
import com.prelude.interview.domain.InterviewSession;
import com.prelude.interview.application.port.InterviewSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class InterviewSessionAccess {

    private static final String STATUS_ONGOING = "ongoing";

    private final InterviewSessionRepository interviewSessionRepository;
    private final CurrentAccount currentAccount;

    public long currentAccountId() {
        return currentAccount.requireId();
    }

    public InterviewSession requireOwned(Long sessionId, long accountId) {
        InterviewSession session = interviewSessionRepository.selectById(sessionId);
        if (session == null || accountId != session.getAccountId()) {
            throw BusinessException.badRequest("面试会话不存在或无权访问");
        }
        return session;
    }

    public InterviewSession requireOngoing(Long sessionId, long accountId) {
        InterviewSession session = requireOwned(sessionId, accountId);
        if (!STATUS_ONGOING.equals(session.getStatus())) {
            throw BusinessException.badRequest("面试会话已结束");
        }
        return session;
    }
}
