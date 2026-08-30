package com.prelude.voice.api;

import com.prelude.interview.domain.InterviewSession;
import com.prelude.interview.application.port.InterviewSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Objects;

@Service
@RequiredArgsConstructor
public class VoiceInterviewSessionService {

    private static final String STATUS_ONGOING = "ongoing";

    private final InterviewSessionRepository interviewSessionRepository;

    public InterviewSession validateActiveSession(Long accountId, Long sessionId) {
        if (accountId == null || sessionId == null) {
            return null;
        }
        InterviewSession interviewSession = interviewSessionRepository.selectById(sessionId);
        if (interviewSession == null || !Objects.equals(interviewSession.getAccountId(), accountId)) {
            return null;
        }
        if (!STATUS_ONGOING.equals(interviewSession.getStatus())) {
            return null;
        }
        return interviewSession;
    }
}
