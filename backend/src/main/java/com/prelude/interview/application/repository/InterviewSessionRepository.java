package com.prelude.interview.application.repository;

import com.prelude.interview.domain.InterviewSession;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

public interface InterviewSessionRepository {

    InterviewSession selectById(Serializable sessionId);

    int add(InterviewSession session);

    int update(InterviewSession session);

    int markGeneratingIfOngoing(Long sessionId, Long accountId);

    /**
     * Persists only the sliding-window summary, so an async writer cannot roll back columns
     * that changed while the model call was in flight.
     */
    int updateSummary(Long sessionId, String summary);

    int updatePinnedAt(Long sessionId, Long accountId, LocalDateTime pinnedAt);

    int deleteOwned(Long sessionId, Long accountId);

    List<InterviewSession> listByUser(Long accountId);
}
