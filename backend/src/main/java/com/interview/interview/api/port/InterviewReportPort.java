package com.interview.interview.api.port;

import com.interview.interview.domain.InterviewMessage;
import com.interview.interview.domain.InterviewSession;
import com.interview.interview.domain.InterviewStage;

import java.util.List;

public interface InterviewReportPort {

    InterviewSession findSession(Long sessionId);

    List<InterviewMessage> listMessages(Long sessionId);

    void closeCurrentStage(Long sessionId);

    List<InterviewStage> listStages(Long sessionId);

    void completeReport(Long sessionId, String reportJson);

    void restoreOngoing(Long sessionId);
}
