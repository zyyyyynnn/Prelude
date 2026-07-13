package com.interview.interview.api.voice;

import com.interview.interview.domain.InterviewSession;
import com.interview.interview.application.port.InterviewSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Objects;

@Service
@RequiredArgsConstructor
public class VoiceInterviewSessionService {

    private static final String STATUS_ONGOING = "ongoing";

    private final InterviewSessionRepository interviewSessionRepository;

    public InterviewSession validateActiveSession(Long userId, Long sessionId) {
        if (userId == null || sessionId == null) {
            return null;
        }
        InterviewSession interviewSession = interviewSessionRepository.selectById(sessionId);
        if (interviewSession == null || !Objects.equals(interviewSession.getUserId(), userId)) {
            return null;
        }
        if (!STATUS_ONGOING.equals(interviewSession.getStatus())) {
            return null;
        }
        return interviewSession;
    }
}
