package com.prelude.interview.application.port;

import com.prelude.interview.domain.InterviewStage;

import java.util.List;

public interface InterviewStageRepository {

    int add(InterviewStage stage);

    int update(InterviewStage stage);

    InterviewStage findCurrent(Long sessionId);

    InterviewStage findLatest(Long sessionId);

    List<InterviewStage> listBySession(Long sessionId);
}
