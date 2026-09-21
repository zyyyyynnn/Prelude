package com.prelude.interview.application;

import com.prelude.interview.application.port.InterviewSessionGuard;
import com.prelude.interview.application.repository.InterviewSessionRepository;
import com.prelude.interview.domain.InterviewSession;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
@RequiredArgsConstructor
public class InterviewSessionGuardAdapter implements InterviewSessionGuard {

    private static final String STATUS_ONGOING = "ongoing";

    private final InterviewSessionRepository interviewSessionRepository;

    @Override
    public boolean isOngoing(Long accountId, Long sessionId) {
        if (accountId == null || sessionId == null) {
            return false;
        }
        InterviewSession session = interviewSessionRepository.selectById(sessionId);
        return session != null
            && Objects.equals(session.getAccountId(), accountId)
            && STATUS_ONGOING.equals(session.getStatus());
    }
}
