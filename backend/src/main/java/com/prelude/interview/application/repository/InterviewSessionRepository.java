package com.prelude.interview.application.repository;

import com.prelude.interview.domain.InterviewSession;
import com.prelude.interview.integration.InterviewSessionStatus;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

public interface InterviewSessionRepository {

    InterviewSession selectById(Serializable sessionId);

    InterviewSession selectByIdForUpdate(Serializable sessionId);

    int add(InterviewSession session);

    int update(InterviewSession session);

    int markGeneratingIfOngoing(Long sessionId, Long accountId);

    InterviewSessionStatus lockAppendOrder(Long sessionId);

    int updateSummary(Long sessionId, String summary);

    int updatePinnedAt(Long sessionId, Long accountId, LocalDateTime pinnedAt);

    int completeReport(Long sessionId, String reportJson);

    int restoreOngoing(Long sessionId);

    int deleteOwned(Long sessionId, Long accountId);

    List<InterviewSession> listByUser(Long accountId);
}
