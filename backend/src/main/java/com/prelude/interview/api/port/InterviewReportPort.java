package com.prelude.interview.api.port;

import com.prelude.interview.domain.InterviewMessage;
import com.prelude.interview.domain.InterviewSession;
import com.prelude.interview.domain.InterviewStage;

import java.util.List;

public interface InterviewReportPort {

    InterviewSession findSession(Long sessionId);

    List<InterviewMessage> listMessages(Long sessionId);

    void closeCurrentStage(Long sessionId);

    List<InterviewStage> listStages(Long sessionId);

    boolean completeReport(Long sessionId, String reportJson);

    void restoreOngoing(Long sessionId);
}
