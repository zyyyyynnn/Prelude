package com.prelude.interview.application;

import com.prelude.interview.application.port.InterviewSessionRepository;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PinInterviewSession {

    private final InterviewSessionAccess sessionAccess;
    private final InterviewSessionRepository interviewSessionRepository;

    public void execute(Long sessionId, boolean pinned) {
        Long accountId = sessionAccess.currentAccountId();
        sessionAccess.requireOwned(sessionId, accountId);
        interviewSessionRepository.updatePinnedAt(
            sessionId, accountId, pinned ? LocalDateTime.now() : null);
    }
}
