package com.prelude.interview.application.port;

import com.prelude.interview.domain.InterviewSession;

import java.io.Serializable;
import java.util.List;

public interface InterviewSessionRepository {

    InterviewSession selectById(Serializable sessionId);

    int add(InterviewSession session);

    int update(InterviewSession session);

    List<InterviewSession> listByUser(Long accountId);
}
