package com.prelude.interview.application.repository;

import com.prelude.interview.domain.InterviewMessage;

import java.time.LocalDateTime;
import java.util.List;

public interface InterviewMessageRepository {

    int add(InterviewMessage message);

    int updateAssessmentIfPending(InterviewMessage message);

    List<InterviewMessage> findPendingForJudge(LocalDateTime cutoff, int limit);

    InterviewMessage findLatest(Long sessionId);

    /** The highest number in the session, read as of now — see the mapper's note on snapshots. */
    InterviewMessage findLatestForAppend(Long sessionId);

    InterviewMessage findById(Long messageId);

    InterviewMessage findByReplyToMessageId(Long userMessageId);

    int markSuperseded(Long messageId, Long supersedingMessageId);

    List<InterviewMessage> listBySession(Long sessionId);

    long countConversationMessages(Long sessionId);

    long countPendingAssessments(Long sessionId);
}
