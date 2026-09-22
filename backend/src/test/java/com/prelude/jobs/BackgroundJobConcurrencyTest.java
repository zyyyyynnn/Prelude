package com.prelude.jobs;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.prelude.jobs.integration.BackgroundJobOperations;
import com.prelude.jobs.infrastructure.persistence.BackgroundJob;
import com.prelude.jobs.infrastructure.persistence.BackgroundJobMapper;
import com.prelude.jobs.infrastructure.persistence.JobAttemptMapper;
import com.prelude.test.AccountFixtures;
import com.prelude.test.JobFixtures;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "PRELUDE_MYSQL_SMOKE", matches = "true")
@SpringBootTest(properties = "spring.rabbitmq.listener.simple.auto-startup=false")
class BackgroundJobConcurrencyTest {

    @Autowired
    private BackgroundJobOperations jobs;

    @Autowired
    private BackgroundJobMapper jobMapper;

    @Autowired
    private JobAttemptMapper attemptMapper;

    @Autowired
    private BackgroundJobRecoveryService recoveryService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final ExecutorService executor = Executors.newFixedThreadPool(2);

    @AfterEach
    void shutdownExecutor() {
        executor.shutdownNow();
    }

    @Test
    void concurrentClaimsHaveExactlyOneWinner() throws Exception {
        long accountId = createAccount();
        var ref = request(accountId, 201L);
        CyclicBarrier start = new CyclicBarrier(2);

        List<Future<BackgroundJobOperations.ClaimOutcome>> futures = List.of(
            executor.submit(raced(start, () -> jobs.claim(ref.jobId()))),
            executor.submit(raced(start, () -> jobs.claim(ref.jobId())))
        );

        long winners = futures.stream().map(this::get).filter(BackgroundJobOperations.ClaimOutcome::claimed).count();
        assertThat(winners).isEqualTo(1);
        assertThat(stored(ref.jobId()).getAttemptCount()).isEqualTo(1);
        assertThat(attempts(ref.jobId())).hasSize(1);
    }

    @Test
    void cancelAndClaimRaceHasExactlyOneWinner() throws Exception {
        long accountId = createAccount();
        var ref = request(accountId, 202L);
        CyclicBarrier start = new CyclicBarrier(2);

        Future<BackgroundJobOperations.BackgroundJobView> cancel = executor.submit(raced(start, () -> jobs.cancel(ref.jobId(), accountId)));
        Future<BackgroundJobOperations.ClaimOutcome> claim = executor.submit(raced(start, () -> jobs.claim(ref.jobId())));

        boolean cancelled = JobFixtures.statusCancelled().equals(cancel.get().status());
        boolean claimed = claim.get().claimed();
        assertThat(cancelled ^ claimed).isTrue();
        assertThat(stored(ref.jobId()).getStatus()).isEqualTo(cancelled
            ? JobFixtures.statusCancelled()
            : JobFixtures.statusRunning());
    }

    @Test
    void completionAndStaleRecoveryCannotBothMutateTheAttempt() throws Exception {
        long accountId = createAccount();
        var ref = request(accountId, 203L);
        var claim = jobs.claim(ref.jobId());
        assertThat(claim.claimed()).isTrue();
        var job = stored(ref.jobId());
        job.setLeaseExpiresAt(LocalDateTime.now().minusMinutes(1));
        jobMapper.updateById(job);
        LocalDateTime now = LocalDateTime.now();
        CyclicBarrier start = new CyclicBarrier(2);

        Future<Void> complete = executor.submit(raced(start, () -> {
            jobs.complete(ref.jobId(), claim.attemptNumber());
            return null;
        }));
        Future<BackgroundJobRecoveryService.RecoveryOutcome> recover = executor.submit(
            raced(start, () -> recoveryService.recover(ref.jobId(), now)));
        complete.get();
        recover.get();

        var finalJob = stored(ref.jobId());
        var attempt = attempts(ref.jobId()).getFirst();
        if (JobFixtures.statusSucceeded().equals(finalJob.getStatus())) {
            assertThat(JobFixtures.attemptStatus(attempt)).isEqualTo(JobFixtures.statusSucceeded());
        } else {
            assertThat(finalJob.getStatus()).isEqualTo(JobFixtures.statusPending());
            assertThat(JobFixtures.attemptStatus(attempt)).isEqualTo(JobFixtures.statusInterrupted());
        }
    }

    @Test
    void staleAttemptCannotCompleteFailOrRenewTheReplacementAttempt() {
        long accountId = createAccount();
        var ref = request(accountId, 204L);
        var first = jobs.claim(ref.jobId());
        assertThat(first.claimed()).isTrue();

        BackgroundJob running = stored(ref.jobId());
        running.setLeaseExpiresAt(LocalDateTime.now().minusMinutes(1));
        jobMapper.updateById(running);
        assertThat(recoveryService.recover(ref.jobId(), LocalDateTime.now()))
            .isEqualTo(BackgroundJobRecoveryService.RecoveryOutcome.RETRY_SCHEDULED);

        var second = jobs.claim(ref.jobId());
        assertThat(second.claimed()).isTrue();
        assertThat(second.attemptNumber()).isEqualTo(first.attemptNumber() + 1);

        jobs.complete(ref.jobId(), first.attemptNumber());
        assertThat(jobs.fail(ref.jobId(), first.attemptNumber(), new RuntimeException("late worker")))
            .isEqualTo(BackgroundJobOperations.FailureOutcome.NOT_RUNNING);
        assertThat(jobs.renewLease(ref.jobId(), first.attemptNumber())).isFalse();

        BackgroundJob stillSecondAttempt = stored(ref.jobId());
        assertThat(stillSecondAttempt.getStatus()).isEqualTo(JobFixtures.statusRunning());
        assertThat(stillSecondAttempt.getAttemptCount()).isEqualTo(second.attemptNumber());
        assertThat(jobs.renewLease(ref.jobId(), second.attemptNumber())).isTrue();

        jobs.complete(ref.jobId(), second.attemptNumber());
        BackgroundJob completed = stored(ref.jobId());
        assertThat(completed.getStatus()).isEqualTo(JobFixtures.statusSucceeded());
        assertThat(JobFixtures.attemptStatuses(attemptMapper, ref.jobId()))
            .containsExactly(JobFixtures.statusInterrupted(), JobFixtures.statusSucceeded());
    }

    @Test
    void renewedLeaseIsNotRecoveredWhileAnExpiredLeaseIs() {
        long accountId = createAccount();
        var activeRef = request(accountId, 205L);
        var active = jobs.claim(activeRef.jobId());
        assertThat(jobs.renewLease(activeRef.jobId(), active.attemptNumber())).isTrue();
        assertThat(recoveryService.recover(activeRef.jobId(), LocalDateTime.now()))
            .isEqualTo(BackgroundJobRecoveryService.RecoveryOutcome.NOT_STALE);
        assertThat(stored(activeRef.jobId()).getStatus()).isEqualTo(JobFixtures.statusRunning());

        var expiredRef = request(accountId, 206L);
        var expired = jobs.claim(expiredRef.jobId());
        BackgroundJob expiredJob = stored(expiredRef.jobId());
        expiredJob.setLeaseExpiresAt(LocalDateTime.now().minusSeconds(1));
        jobMapper.updateById(expiredJob);

        assertThat(recoveryService.recover(expiredRef.jobId(), LocalDateTime.now()))
            .isEqualTo(BackgroundJobRecoveryService.RecoveryOutcome.RETRY_SCHEDULED);
        assertThat(stored(expiredRef.jobId()).getStatus()).isEqualTo(JobFixtures.statusPending());
        assertThat(JobFixtures.attemptStatus(attempts(expiredRef.jobId()).getFirst())).isEqualTo(JobFixtures.statusInterrupted());
        assertThat(expired.attemptNumber()).isEqualTo(1);
    }

    private <T> Callable<T> raced(CyclicBarrier barrier, Callable<T> action) {
        return () -> {
            barrier.await();
            return action.call();
        };
    }

    private <T> T get(Future<T> future) {
        try {
            return future.get();
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
    }

    private BackgroundJobOperations.BackgroundJobRef request(long accountId, long subjectId) {
        return jobs.request(new BackgroundJobOperations.BackgroundJobRequest(
            "test.concurrent", accountId, subjectId,
            "test.concurrent:operation:" + System.nanoTime(), "{}"));
    }

    private BackgroundJob stored(String jobId) {
        return jobMapper.selectOne(new LambdaQueryWrapper<BackgroundJob>()
            .eq(BackgroundJob::getJobId, jobId)
            .last("LIMIT 1"));
    }

    private List<?> attempts(String jobId) {
        return JobFixtures.attempts(attemptMapper, jobId);
    }

    private long createAccount() {
        return AccountFixtures.create(jdbcTemplate, "jobs-race");
    }
}
