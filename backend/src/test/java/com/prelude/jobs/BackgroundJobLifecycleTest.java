package com.prelude.jobs;

import com.prelude.jobs.integration.BackgroundJobOperations;
import com.prelude.jobs.infrastructure.persistence.BackgroundJobMapper;
import com.prelude.test.AccountFixtures;
import com.prelude.test.JobFixtures;
import com.prelude.test.ExceptionFixtures;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Durable background job lifecycle against real MySQL + the real Spring
 * Modulith publication registry: the dispatch event is published inside the
 * requesting transaction, persists as an EVENT_PUBLICATION row, and claims /
 * retries / duplicates / cancellation stay owned by the jobs executor. The
 * framework owns externalization; jobs never calls RabbitTemplate.
 */
@EnabledIfEnvironmentVariable(named = "PRELUDE_MYSQL_SMOKE", matches = "true")
@SpringBootTest(properties = "spring.rabbitmq.listener.simple.auto-startup=false")
@RecordApplicationEvents
class BackgroundJobLifecycleTest {

    @Autowired
    private BackgroundJobOperations jobs;

    @Autowired
    private BackgroundJobMapper jobMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private BackgroundJobRecoveryService recoveryService;

    @Autowired
    private ApplicationEvents applicationEvents;

    private long createAccount() {
        return AccountFixtures.create(jdbcTemplate, "jobs");
    }

    private String uniqueOperationKey() {
        return "test.lifecycle:operation:" + System.nanoTime();
    }

    private BackgroundJobOperations.BackgroundJobRef requestJob(long accountId, long subjectId, String operationKey) {
        return jobs.request(new BackgroundJobOperations.BackgroundJobRequest(
            "test.lifecycle", accountId, subjectId, operationKey, "{}"));
    }

    private BackgroundJobOperations.BackgroundJobRef requestJob(long accountId, long subjectId) {
        return requestJob(accountId, subjectId, uniqueOperationKey());
    }

    private com.prelude.jobs.infrastructure.persistence.BackgroundJob stored(String jobId) {
        return JobFixtures.stored(jobMapper, jobId);
    }

    private long publicationRowsFor(String jobId) {
        Long count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM EVENT_PUBLICATION WHERE SERIALIZED_EVENT LIKE ?",
            Long.class, "%" + jobId + "%");
        return count == null ? 0 : count;
    }

    @Test
    void requestPersistsPendingJobAndDurablePublication() {
        long accountId = createAccount();
        var ref = requestJob(accountId, 42L);

        var job = stored(ref.jobId());
        assertThat(job.getStatus()).isEqualTo(JobFixtures.statusPending());
        assertThat(job.getAttemptCount()).isZero();
        // The dispatch event persisted in the same transaction: broker-down
        // recovery has a durable anchor.
        assertThat(publicationRowsFor(ref.jobId())).isGreaterThanOrEqualTo(1);
    }

    @Test
    void duplicateOperationKeyReturnsTheSameLogicalJobWithoutDuplicateWork() {
        long accountId = createAccount();
        String operationKey = uniqueOperationKey();

        var first = requestJob(accountId, 43L, operationKey);
        var second = requestJob(accountId, 43L, operationKey);

        assertThat(second.jobId()).isEqualTo(first.jobId());
        long rows = JobFixtures.countByOperationKey(jobMapper, operationKey);
        assertThat(rows).isEqualTo(1);
    }

    @Test
    void duplicateDeliveryAbsorbsBusinessSideEffects() {
        long accountId = createAccount();
        var ref = requestJob(accountId, 45L);

        var first = jobs.claim(ref.jobId());
        jobs.complete(ref.jobId(), first.attemptNumber());
        var replay = jobs.claim(ref.jobId());

        assertThat(first.claimed()).isTrue();
        assertThat(replay.claimed()).isFalse();
        var job = stored(ref.jobId());
        assertThat(job.getStatus()).isEqualTo(JobFixtures.statusSucceeded());
        assertThat(job.getAttemptCount()).isEqualTo(1);
        assertThat(applicationEvents.stream(JobFixtures.succeededEventClass())
            .filter(event -> event.jobId().equals(ref.jobId())).count()).isEqualTo(1);
        jobs.complete(ref.jobId(), first.attemptNumber());
        assertThat(applicationEvents.stream(JobFixtures.succeededEventClass())
            .filter(event -> event.jobId().equals(ref.jobId())).count()).isEqualTo(1);
    }

    @Test
    void boundedRetryReturnsToPendingAndRepublishesUntilExhausted() {
        long accountId = createAccount();
        var ref = requestJob(accountId, 46L);

        for (int attempt = 1; attempt <= 3; attempt++) {
            var claim = jobs.claim(ref.jobId());
            assertThat(claim.claimed()).isTrue();
            var outcome = jobs.fail(
                ref.jobId(), claim.attemptNumber(), new RuntimeException("transient failure"));
            assertThat(outcome).isEqualTo(attempt < 3
                ? BackgroundJobOperations.FailureOutcome.RETRY_SCHEDULED
                : BackgroundJobOperations.FailureOutcome.TERMINAL_FAILED);
        }

        var job = stored(ref.jobId());
        // Three attempts exhausted the max: terminal FAILED, no further dispatch.
        assertThat(job.getStatus()).isEqualTo(JobFixtures.statusFailed());
        assertThat(job.getAttemptCount()).isEqualTo(3);
        // Original request + the two bounded retries produced durable publications.
        assertThat(publicationRowsFor(ref.jobId())).isGreaterThanOrEqualTo(3);
        assertThat(applicationEvents.stream(JobFixtures.failedEventClass())
            .filter(event -> event.jobId().equals(ref.jobId())).count()).isEqualTo(1);
        assertThat(jobs.fail(ref.jobId(), 3, new RuntimeException("duplicate terminal failure")))
            .isEqualTo(BackgroundJobOperations.FailureOutcome.NOT_RUNNING);
        assertThat(applicationEvents.stream(JobFixtures.failedEventClass())
            .filter(event -> event.jobId().equals(ref.jobId())).count()).isEqualTo(1);
    }

    @Test
    void persistedFailureSummaryRedactsSecretsAtTheJobsBoundary() {
        long accountId = createAccount();
        var ref = requestJob(accountId, 47L);
        var claim = jobs.claim(ref.jobId());
        assertThat(claim.claimed()).isTrue();

        jobs.fail(ref.jobId(), claim.attemptNumber(), new RuntimeException(
            "Bearer secret-token apiKey=sk-supersecret123 https://user:pass@example.com/v1?token=abc"));

        var job = stored(ref.jobId());
        assertThat(job.getLastError())
            .contains("Bearer [REDACTED]")
            .contains("apiKey=[REDACTED]")
            .contains("https://[REDACTED]@example.com/v1?[REDACTED]")
            .doesNotContain("secret-token", "sk-supersecret123", "user:pass", "token=abc");
    }

    @Test
    void crossAccountJobAccessIsNotFoundEquivalent() {
        long owner = createAccount();
        long other = createAccount();
        var ref = requestJob(owner, 48L);

        ExceptionFixtures.assertBusinessException(() -> jobs.view(ref.jobId(), other), "not_found");
        ExceptionFixtures.assertBusinessException(() -> jobs.cancel(ref.jobId(), other), "not_found");
    }

    @Test
    void staleRunningRecoveryInterruptsTheAttemptAndRedispatches() {
        long accountId = createAccount();
        var ref = requestJob(accountId, 49L);
        var claim = jobs.claim(ref.jobId());
        assertThat(claim.claimed()).isTrue();

        var running = stored(ref.jobId());
        running.setLeaseExpiresAt(java.time.LocalDateTime.now().minusMinutes(1));
        jobMapper.updateById(running);

        recoveryService.recover(ref.jobId(), java.time.LocalDateTime.now());

        var recovered = stored(ref.jobId());
        // The interrupted attempt was closed and the job redispatched via the
        // same reliable event path (a fresh durable publication exists).
        assertThat(recovered.getStatus()).isEqualTo(JobFixtures.statusPending());
        assertThat(publicationRowsFor(ref.jobId())).isGreaterThanOrEqualTo(2);
        var view = jobs.view(ref.jobId(), accountId);
        assertThat(view.attemptCount()).isEqualTo(1);
    }

    @Test
    void cancellationReturnsAndPublishesTheAuthoritativeTerminalState() {
        long accountId = createAccount();
        var ref = requestJob(accountId, 50L);

        var cancelled = jobs.cancel(ref.jobId(), accountId);

        assertThat(cancelled.status()).isEqualTo(JobFixtures.statusCancelled());
        assertThat(applicationEvents.stream(JobFixtures.cancelledEventClass())
            .filter(event -> event.jobId().equals(ref.jobId())).count()).isEqualTo(1);
        assertThat(jobs.cancel(ref.jobId(), accountId).status()).isEqualTo(JobFixtures.statusCancelled());
        assertThat(applicationEvents.stream(JobFixtures.cancelledEventClass())
            .filter(event -> event.jobId().equals(ref.jobId())).count()).isEqualTo(1);
    }

    @Test
    void expiredFinalLeasePublishesTheSameTerminalFailureEventAsWorkerFailure() {
        long accountId = createAccount();
        var ref = requestJob(accountId, 51L);
        var claim = jobs.claim(ref.jobId());
        var running = stored(ref.jobId());
        running.setMaxAttempts(claim.attemptNumber());
        running.setLeaseExpiresAt(java.time.LocalDateTime.now().minusSeconds(1));
        jobMapper.updateById(running);

        assertThat(recoveryService.recover(ref.jobId(), java.time.LocalDateTime.now()))
            .isEqualTo(BackgroundJobRecoveryService.RecoveryOutcome.TERMINAL_FAILED);

        assertThat(stored(ref.jobId()).getStatus()).isEqualTo(JobFixtures.statusFailed());
        assertThat(applicationEvents.stream(JobFixtures.failedEventClass())
            .filter(event -> event.jobId().equals(ref.jobId())).count()).isEqualTo(1);
    }
}
