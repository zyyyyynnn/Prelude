package com.prelude.jobs.application.port;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Durable job-row access for both the execution path (claims, lease renewal, terminal
 * transitions) and the stale-claim recovery path. Speaks in the job's own fields rather
 * than the persistence row type, so no use case names the persistence package.
 *
 * <p>Every transition is a compare-and-set guarded by status and attempt count and
 * reports the rows it wrote, so a stale caller that lost the race is told rather than
 * silently overwriting a newer state.
 */
public interface BackgroundJobStore {

    String PENDING = "PENDING";
    String RUNNING = "RUNNING";
    String SUCCEEDED = "SUCCEEDED";
    String FAILED = "FAILED";
    String CANCELLED = "CANCELLED";

    int DEFAULT_MAX_ATTEMPTS = 3;

    /** The job carrying this operation key, for duplicate absorption. */
    Optional<JobRow> findByOperationKey(String operationKey);

    Optional<JobRow> findByJobId(String jobId);

    /** Ids of jobs whose lease has expired, bounded by {@code limit}. */
    List<String> findExpiredLeaseJobIds(LocalDateTime now, int limit);

    /** Inserts a PENDING job and reports the row it wrote. */
    JobRow insertPending(String type, Long accountId, Long subjectId, String operationKey, String payloadJson);

    /**
     * Claims a PENDING job for the next attempt and records the attempt row in the same
     * call. Returns empty when the job is no longer PENDING at the expected attempt count.
     */
    Optional<ClaimResult> claim(String jobId, LocalDateTime claimedAt, LocalDateTime leaseExpiresAt);

    /** Extends the lease of a RUNNING attempt. Returns the rows written. */
    int renewLease(String jobId, int attemptNumber, LocalDateTime leaseExpiresAt);

    /** Succeeds a RUNNING attempt and closes its attempt row. Returns the rows written. */
    int complete(String jobId, int attemptNumber, LocalDateTime finishedAt);

    /**
     * Fails a RUNNING attempt: back to PENDING while attempts remain, otherwise terminal.
     * Closes the attempt row either way. Returns the rows written.
     */
    int fail(String jobId, int attemptNumber, String sanitizedFailure, LocalDateTime finishedAt, boolean retry);

    /** Cancels a PENDING job. Returns the rows written. */
    int cancel(String jobId, LocalDateTime finishedAt);

    /**
     * The stale-claim transition: only a job that is still RUNNING, on the same attempt
     * count and past its lease may move. Returns the rows written.
     */
    int transitionStaleClaim(JobRow job, LocalDateTime now, boolean retry, String reason);

    /** Marks the interrupted attempt of that job, only while it is still RUNNING. */
    void interruptRunningAttempt(String jobId, int attemptNumber, String reason, LocalDateTime finishedAt);

    /** The job row as the execution and recovery paths see it. */
    record JobRow(
        String jobId,
        String type,
        Long accountId,
        Long subjectId,
        String status,
        int attemptCount,
        int maxAttempts,
        LocalDateTime leaseExpiresAt,
        String lastError
    ) {
    }

    /** A successful claim: which attempt number the worker now owns. */
    record ClaimResult(int attemptNumber) {
    }
}
