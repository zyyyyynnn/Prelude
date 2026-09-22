package com.prelude.interview.api.port;

import java.util.List;

/**
 * The interview facts the report pipeline needs. Every type in this contract is a
 * snapshot, so a consumer never names an interview domain class or a persistence row.
 */
public interface InterviewReportPort {

    InterviewSessionSnapshot findSession(Long sessionId);

    List<InterviewMessageSnapshot> listMessages(Long sessionId);

    void closeCurrentStage(Long sessionId);

    List<InterviewStageSnapshot> listStages(Long sessionId);

    boolean completeReport(Long sessionId, String reportJson);

    void restoreOngoing(Long sessionId);
}
