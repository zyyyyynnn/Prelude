package com.prelude.interview.application.repository;

import com.prelude.interview.domain.InterviewSession;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

public interface InterviewSessionRepository {

    InterviewSession selectById(Serializable sessionId);

    InterviewSession selectByIdForUpdate(Serializable sessionId);

    int add(InterviewSession session);

    int update(InterviewSession session);

    int markGeneratingIfOngoing(Long sessionId, Long accountId);

    /** Locks the session for the current transaction and returns its current status. */
    String lockAppendOrder(Long sessionId);

    /** Persists only the summary so an async writer cannot overwrite concurrent columns. */
    int updateSummary(Long sessionId, String summary);

    int updatePinnedAt(Long sessionId, Long accountId, LocalDateTime pinnedAt);

    /** Fenced report commit: only a still-generating session may be finished. */
    int completeReportIfGenerating(Long sessionId, String reportJson);

    /** Fenced rollback: only a still-generating session may go back to ongoing. */
    int restoreOngoingIfGenerating(Long sessionId);

    int deleteOwned(Long sessionId, Long accountId);

    List<InterviewSession> listByUser(Long accountId);
}
