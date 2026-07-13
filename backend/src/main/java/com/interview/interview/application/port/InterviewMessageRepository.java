package com.interview.interview.application.port;

import com.interview.interview.domain.InterviewMessage;

import java.io.Serializable;
import java.util.List;

public interface InterviewMessageRepository {

    int add(InterviewMessage message);

    int update(InterviewMessage message);

    int delete(Serializable messageId);

    InterviewMessage findLatest(Long sessionId);

    List<InterviewMessage> listBySession(Long sessionId);

    long countConversationMessages(Long sessionId);
}
