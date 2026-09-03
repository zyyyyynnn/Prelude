package com.prelude.jobs;

import com.prelude.jobs.integration.BackgroundJobOperations;
import com.prelude.jobs.integration.BackgroundJobOperations.BackgroundJobRef;
import com.prelude.jobs.integration.BackgroundJobOperations.BackgroundJobRequest;
import com.prelude.jobs.integration.BackgroundJobOperations.BackgroundJobView;
import com.prelude.jobs.integration.BackgroundJobOperations.ClaimOutcome;
import com.prelude.jobs.integration.BackgroundJobOperations.FailureOutcome;
import com.prelude.jobs.integration.BackgroundJobFailed;
import com.prelude.jobs.integration.BackgroundJobCancelled;
import com.prelude.jobs.integration.BackgroundJobSucceeded;
import com.prelude.jobs.persistence.BackgroundJob;
import com.prelude.jobs.persistence.BackgroundJobMapper;
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
    private com.prelude.identity.AccountMapper accountMapper;

    @Autowired
    private BackgroundJobRecoveryService recoveryService;

    @Autowired
    private ApplicationEvents applicationEvents;

    private long createAccount() {
        com.prelude.identity.Account account = new com.prelude.identity.Account();
        account.setUsername("jobs-" + System.nanoTime());
        account.setRevision(0L);
        accountMapper.insert(account);
        return account.getId();
    }

    private String uniqueOperationKey() {
        return "test.lifecycle:operation:" + System.nanoTime();
    }

    private BackgroundJob stored(String jobId) {
        return jobMapper.selectOne(
            new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<BackgroundJob>()
                .eq(BackgroundJob::getJobId, jobId)
                .last("LIMIT 1"));
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
        BackgroundJobRef ref = jobs.request(new BackgroundJobRequest(
            "test.lifecycle", accountId, 42L, uniqueOperationKey(), "{}"));

        BackgroundJob job = stored(ref.jobId());
        assertThat(job.getStatus()).isEqualTo(BackgroundJob.PENDING);
        assertThat(job.getAttemptCount()).isZero();
        // The dispatch event persisted in the same transaction: broker-down
        // recovery has a durable anchor.
        assertThat(publicationRowsFor(ref.jobId())).isGreaterThanOrEqualTo(1);
    }

    @Test
    void duplicateOperationKeyReturnsTheSameLogicalJobWithoutDuplicateWork() {
        long accountId = createAccount();
        String operationKey = uniqueOperationKey();
        BackgroundJobRequest request = new BackgroundJobRequest(
            "test.lifecycle", accountId, 43L, operationKey, "{}");

        BackgroundJobRef first = jobs.request(request);
        BackgroundJobRef second = jobs.request(request);

        assertThat(second.jobId()).isEqualTo(first.jobId());
        long rows = jobMapper.selectCount(
            new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<BackgroundJob>()
                .eq(BackgroundJob::getOperationKey, operationKey));
        assertThat(rows).isEqualTo(1);
    }

    @Test
    void duplicateDeliveryAbsorbsBusinessSideEffects() {
        long accountId = createAccount();
        BackgroundJobRef ref = jobs.request(new BackgroundJobRequest(
            "test.lifecycle", accountId, 45L, uniqueOperationKey(), "{}"));

        ClaimOutcome first = jobs.claim(ref.jobId());
        jobs.complete(ref.jobId(), first.attemptNumber());
        ClaimOutcome replay = jobs.claim(ref.jobId());

        assertThat(first.claimed()).isTrue();
        assertThat(replay.claimed()).isFalse();
        BackgroundJob job = stored(ref.jobId());
        assertThat(job.getStatus()).isEqualTo(BackgroundJob.SUCCEEDED);
        assertThat(job.getAttemptCount()).isEqualTo(1);
        assertThat(applicationEvents.stream(BackgroundJobSucceeded.class)
            .filter(event -> event.jobId().equals(ref.jobId())).count()).isEqualTo(1);
        jobs.complete(ref.jobId(), first.attemptNumber());
        assertThat(applicationEvents.stream(BackgroundJobSucceeded.class)
            .filter(event -> event.jobId().equals(ref.jobId())).count()).isEqualTo(1);
    }

    @Test
    void boundedRetryReturnsToPendingAndRepublishesUntilExhausted() {
        long accountId = createAccount();
        BackgroundJobRef ref = jobs.request(new BackgroundJobRequest(
            "test.lifecycle", accountId, 46L, uniqueOperationKey(), "{}"));

        for (int attempt = 1; attempt <= 3; attempt++) {
            ClaimOutcome claim = jobs.claim(ref.jobId());
            assertThat(claim.claimed()).isTrue();
            FailureOutcome outcome = jobs.fail(
                ref.jobId(), claim.attemptNumber(), new RuntimeException("transient failure"));
            assertThat(outcome).isEqualTo(attempt < 3
                ? FailureOutcome.RETRY_SCHEDULED
                : FailureOutcome.TERMINAL_FAILED);
        }

        BackgroundJob job = stored(ref.jobId());
        // Three attempts exhausted the max: terminal FAILED, no further dispatch.
        assertThat(job.getStatus()).isEqualTo(BackgroundJob.FAILED);
        assertThat(job.getAttemptCount()).isEqualTo(3);
        // Original request + the two bounded retries produced durable publications.
        assertThat(publicationRowsFor(ref.jobId())).isGreaterThanOrEqualTo(3);
        assertThat(applicationEvents.stream(BackgroundJobFailed.class)
            .filter(event -> event.jobId().equals(ref.jobId())).count()).isEqualTo(1);
        assertThat(jobs.fail(ref.jobId(), 3, new RuntimeException("duplicate terminal failure")))
            .isEqualTo(FailureOutcome.NOT_RUNNING);
        assertThat(applicationEvents.stream(BackgroundJobFailed.class)
            .filter(event -> event.jobId().equals(ref.jobId())).count()).isEqualTo(1);
    }

    @Test
    void persistedFailureSummaryRedactsSecretsAtTheJobsBoundary() {
        long accountId = createAccount();
        BackgroundJobRef ref = jobs.request(new BackgroundJobRequest(
            "test.lifecycle", accountId, 47L, uniqueOperationKey(), "{}"));
        ClaimOutcome claim = jobs.claim(ref.jobId());
        assertThat(claim.claimed()).isTrue();

        jobs.fail(ref.jobId(), claim.attemptNumber(), new RuntimeException(
            "Bearer secret-token apiKey=sk-supersecret123 https://user:pass@example.com/v1?token=abc"));

        BackgroundJob job = stored(ref.jobId());
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
        BackgroundJobRef ref = jobs.request(new BackgroundJobRequest(
            "test.lifecycle", owner, 48L, uniqueOperationKey(), "{}"));

        assertThatThrownBy(() -> jobs.view(ref.jobId(), other))
            .isInstanceOf(com.prelude.BusinessException.class)
            .hasFieldOrPropertyWithValue("code", "not_found");
        assertThatThrownBy(() -> jobs.cancel(ref.jobId(), other))
            .isInstanceOf(com.prelude.BusinessException.class)
            .hasFieldOrPropertyWithValue("code", "not_found");
    }

    @Test
    void staleRunningRecoveryInterruptsTheAttemptAndRedispatches() {
        long accountId = createAccount();
        BackgroundJobRef ref = jobs.request(new BackgroundJobRequest(
            "test.lifecycle", accountId, 49L, uniqueOperationKey(), "{}"));
        ClaimOutcome claim = jobs.claim(ref.jobId());
        assertThat(claim.claimed()).isTrue();

        BackgroundJob running = stored(ref.jobId());
        running.setLeaseExpiresAt(java.time.LocalDateTime.now().minusMinutes(1));
        jobMapper.updateById(running);

        recoveryService.recover(ref.jobId(), java.time.LocalDateTime.now());

        BackgroundJob recovered = stored(ref.jobId());
        // The interrupted attempt was closed and the job redispatched via the
        // same reliable event path (a fresh durable publication exists).
        assertThat(recovered.getStatus()).isEqualTo(BackgroundJob.PENDING);
        assertThat(publicationRowsFor(ref.jobId())).isGreaterThanOrEqualTo(2);
        BackgroundJobView view = jobs.view(ref.jobId(), accountId);
        assertThat(view.attemptCount()).isEqualTo(1);
    }

    @Test
    void cancellationReturnsAndPublishesTheAuthoritativeTerminalState() {
        long accountId = createAccount();
        BackgroundJobRef ref = jobs.request(new BackgroundJobRequest(
            "test.lifecycle", accountId, 50L, uniqueOperationKey(), "{}"));

        BackgroundJobView cancelled = jobs.cancel(ref.jobId(), accountId);

        assertThat(cancelled.status()).isEqualTo(BackgroundJob.CANCELLED);
        assertThat(applicationEvents.stream(BackgroundJobCancelled.class)
            .filter(event -> event.jobId().equals(ref.jobId())).count()).isEqualTo(1);
        assertThat(jobs.cancel(ref.jobId(), accountId).status()).isEqualTo(BackgroundJob.CANCELLED);
        assertThat(applicationEvents.stream(BackgroundJobCancelled.class)
            .filter(event -> event.jobId().equals(ref.jobId())).count()).isEqualTo(1);
    }

    @Test
    void expiredFinalLeasePublishesTheSameTerminalFailureEventAsWorkerFailure() {
        long accountId = createAccount();
        BackgroundJobRef ref = jobs.request(new BackgroundJobRequest(
            "test.lifecycle", accountId, 51L, uniqueOperationKey(), "{}"));
        ClaimOutcome claim = jobs.claim(ref.jobId());
        BackgroundJob running = stored(ref.jobId());
        running.setMaxAttempts(claim.attemptNumber());
        running.setLeaseExpiresAt(java.time.LocalDateTime.now().minusSeconds(1));
        jobMapper.updateById(running);

        assertThat(recoveryService.recover(ref.jobId(), java.time.LocalDateTime.now()))
            .isEqualTo(BackgroundJobRecoveryService.RecoveryOutcome.TERMINAL_FAILED);

        assertThat(stored(ref.jobId()).getStatus()).isEqualTo(BackgroundJob.FAILED);
        assertThat(applicationEvents.stream(BackgroundJobFailed.class)
            .filter(event -> event.jobId().equals(ref.jobId())).count()).isEqualTo(1);
    }
}
