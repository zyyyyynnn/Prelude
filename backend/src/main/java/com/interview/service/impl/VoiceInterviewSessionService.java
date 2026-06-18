package com.interview.service.impl;

import com.interview.entity.InterviewSession;
import com.interview.mapper.InterviewSessionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Objects;

@Service
@RequiredArgsConstructor
public class VoiceInterviewSessionService {

    private static final String STATUS_ONGOING = "ongoing";

    private final InterviewSessionMapper interviewSessionMapper;

    public InterviewSession validateActiveSession(Long userId, Long sessionId) {
        if (userId == null || sessionId == null) {
            return null;
        }
        InterviewSession interviewSession = interviewSessionMapper.selectById(sessionId);
        if (interviewSession == null || !Objects.equals(interviewSession.getUserId(), userId)) {
            return null;
        }
        if (!STATUS_ONGOING.equals(interviewSession.getStatus())) {
            return null;
        }
        return interviewSession;
    }
}
