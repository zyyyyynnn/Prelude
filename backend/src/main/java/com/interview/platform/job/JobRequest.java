package com.interview.platform.job;

public record JobRequest(
    String type,
    Long userId,
    Long subjectId,
    String payloadJson,
    String idempotencyKey
) {

    public static JobRequest report(Long sessionId, Long userId) {
        return new JobRequest(
            JobTypes.REPORT_GENERATE,
            userId,
            sessionId,
            "{}",
            JobTypes.REPORT_GENERATE + ":session:" + sessionId
        );
    }
}
