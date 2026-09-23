package com.prelude.interview.application;

import com.prelude.interview.application.repository.InterviewSessionRepository;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PinInterviewSession {

    private final InterviewSessionAccess sessionAccess;
    private final InterviewSessionRepository interviewSessionRepository;

    @Transactional
    public void execute(Long sessionId, boolean pinned) {
        Long accountId = sessionAccess.currentAccountId();
        sessionAccess.requireOwned(sessionId, accountId);
        interviewSessionRepository.updatePinnedAt(
            sessionId, accountId, pinned ? LocalDateTime.now() : null);
    }
}
