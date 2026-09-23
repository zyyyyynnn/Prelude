package com.prelude.interview.application.repository;

import com.prelude.interview.domain.InterviewMessage;

import java.io.Serializable;
import java.util.List;

public interface InterviewMessageRepository {

    int add(InterviewMessage message);

    int update(InterviewMessage message);

    int delete(Serializable messageId);

    InterviewMessage findLatest(Long sessionId);

    /** The highest number in the session, read as of now — see the mapper's note on snapshots. */
    InterviewMessage findLatestForAppend(Long sessionId);

    InterviewMessage findById(Long messageId);

    List<InterviewMessage> listBySession(Long sessionId);

    long countConversationMessages(Long sessionId);
}
