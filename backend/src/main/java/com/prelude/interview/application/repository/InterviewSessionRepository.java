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
     * Serialises whoever is about to append to this session and reports the status under that
     * lock, or null when the session is missing. Callers hold it for the rest of their
     * transaction, so two appends cannot read the same "last message" and number alike.
     */
    String lockAppendOrder(Long sessionId);

    /**
     * Persists only the sliding-window summary, so an async writer cannot roll back columns
     * that changed while the model call was in flight.
     */
    int updateSummary(Long sessionId, String summary);

    int updatePinnedAt(Long sessionId, Long accountId, LocalDateTime pinnedAt);

    /** Fenced report commit: only a still-generating session may be finished. */
    int completeReportIfGenerating(Long sessionId, String reportJson);

    /** Fenced rollback: only a still-generating session may go back to ongoing. */
    int restoreOngoingIfGenerating(Long sessionId);

    int deleteOwned(Long sessionId, Long accountId);

    List<InterviewSession> listByUser(Long accountId);
}
