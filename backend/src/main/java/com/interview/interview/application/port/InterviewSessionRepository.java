package com.interview.interview.application.port;

import com.interview.interview.domain.InterviewSession;

import java.io.Serializable;
import java.util.List;

public interface InterviewSessionRepository {

    InterviewSession selectById(Serializable sessionId);

    int add(InterviewSession session);

    int update(InterviewSession session);

    InterviewSession findOngoing(Long userId, Long resumeId, Long positionId);

    List<InterviewSession> listByUser(Long userId);
}
