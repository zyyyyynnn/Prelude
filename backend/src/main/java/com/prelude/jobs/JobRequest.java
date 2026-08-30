package com.prelude.jobs;

public record JobRequest(
    String type,
    Long accountId,
    Long subjectId,
    String payloadJson,
    String idempotencyKey
) {

    public static JobRequest report(Long sessionId, Long accountId) {
        return new JobRequest(
            JobTypes.REPORT_GENERATE,
            accountId,
            sessionId,
            "{}",
            JobTypes.REPORT_GENERATE + ":session:" + sessionId
        );
    }
}
