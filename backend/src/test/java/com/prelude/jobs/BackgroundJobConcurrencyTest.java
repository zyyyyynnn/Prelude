package com.prelude.jobs;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.prelude.jobs.integration.BackgroundJobOperations;
import com.prelude.jobs.integration.BackgroundJobOperations.BackgroundJobRef;
import com.prelude.jobs.integration.BackgroundJobOperations.BackgroundJobRequest;
import com.prelude.jobs.integration.BackgroundJobOperations.BackgroundJobView;
import com.prelude.jobs.integration.BackgroundJobOperations.ClaimOutcome;
import com.prelude.jobs.integration.BackgroundJobOperations.FailureOutcome;
import com.prelude.jobs.persistence.BackgroundJob;
import com.prelude.jobs.persistence.BackgroundJobMapper;
import com.prelude.jobs.persistence.JobAttempt;
import com.prelude.jobs.persistence.JobAttemptMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

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
    private com.prelude.identity.AccountMapper accountMapper;

    private final ExecutorService executor = Executors.newFixedThreadPool(2);

    @AfterEach
    void shutdownExecutor() {
        executor.shutdownNow();
    }

    @Test
    void concurrentClaimsHaveExactlyOneWinner() throws Exception {
        long accountId = createAccount();
        BackgroundJobRef ref = request(accountId, 201L);
        CyclicBarrier start = new CyclicBarrier(2);

        List<Future<ClaimOutcome>> futures = List.of(
            executor.submit(raced(start, () -> jobs.claim(ref.jobId()))),
            executor.submit(raced(start, () -> jobs.claim(ref.jobId())))
        );

        long winners = futures.stream().map(this::get).filter(ClaimOutcome::claimed).count();
        assertThat(winners).isEqualTo(1);
        assertThat(stored(ref.jobId()).getAttemptCount()).isEqualTo(1);
        assertThat(attempts(ref.jobId())).hasSize(1);
    }

    @Test
    void cancelAndClaimRaceHasExactlyOneWinner() throws Exception {
        long accountId = createAccount();
        BackgroundJobRef ref = request(accountId, 202L);
        CyclicBarrier start = new CyclicBarrier(2);

        Future<BackgroundJobView> cancel = executor.submit(raced(start, () -> jobs.cancel(ref.jobId(), accountId)));
        Future<ClaimOutcome> claim = executor.submit(raced(start, () -> jobs.claim(ref.jobId())));

        boolean cancelled = BackgroundJob.CANCELLED.equals(cancel.get().status());
        boolean claimed = claim.get().claimed();
        assertThat(cancelled ^ claimed).isTrue();
        assertThat(stored(ref.jobId()).getStatus()).isEqualTo(cancelled
            ? BackgroundJob.CANCELLED
            : BackgroundJob.RUNNING);
    }

    @Test
    void completionAndStaleRecoveryCannotBothMutateTheAttempt() throws Exception {
        long accountId = createAccount();
        BackgroundJobRef ref = request(accountId, 203L);
        ClaimOutcome claim = jobs.claim(ref.jobId());
        assertThat(claim.claimed()).isTrue();
        BackgroundJob job = stored(ref.jobId());
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

        BackgroundJob finalJob = stored(ref.jobId());
        JobAttempt attempt = attempts(ref.jobId()).getFirst();
        if (BackgroundJob.SUCCEEDED.equals(finalJob.getStatus())) {
            assertThat(attempt.getStatus()).isEqualTo(JobAttempt.SUCCEEDED);
        } else {
            assertThat(finalJob.getStatus()).isEqualTo(BackgroundJob.PENDING);
            assertThat(attempt.getStatus()).isEqualTo(JobAttempt.INTERRUPTED);
        }
    }

    @Test
    void staleAttemptCannotCompleteFailOrRenewTheReplacementAttempt() {
        long accountId = createAccount();
        BackgroundJobRef ref = request(accountId, 204L);
        ClaimOutcome first = jobs.claim(ref.jobId());
        assertThat(first.claimed()).isTrue();

        BackgroundJob running = stored(ref.jobId());
        running.setLeaseExpiresAt(LocalDateTime.now().minusMinutes(1));
        jobMapper.updateById(running);
        assertThat(recoveryService.recover(ref.jobId(), LocalDateTime.now()))
            .isEqualTo(BackgroundJobRecoveryService.RecoveryOutcome.RETRY_SCHEDULED);

        ClaimOutcome second = jobs.claim(ref.jobId());
        assertThat(second.claimed()).isTrue();
        assertThat(second.attemptNumber()).isEqualTo(first.attemptNumber() + 1);

        jobs.complete(ref.jobId(), first.attemptNumber());
        assertThat(jobs.fail(ref.jobId(), first.attemptNumber(), new RuntimeException("late worker")))
            .isEqualTo(FailureOutcome.NOT_RUNNING);
        assertThat(jobs.renewLease(ref.jobId(), first.attemptNumber())).isFalse();

        BackgroundJob stillSecondAttempt = stored(ref.jobId());
        assertThat(stillSecondAttempt.getStatus()).isEqualTo(BackgroundJob.RUNNING);
        assertThat(stillSecondAttempt.getAttemptCount()).isEqualTo(second.attemptNumber());
        assertThat(jobs.renewLease(ref.jobId(), second.attemptNumber())).isTrue();

        jobs.complete(ref.jobId(), second.attemptNumber());
        BackgroundJob completed = stored(ref.jobId());
        assertThat(completed.getStatus()).isEqualTo(BackgroundJob.SUCCEEDED);
        assertThat(attempts(ref.jobId())).extracting(JobAttempt::getStatus)
            .containsExactly(JobAttempt.INTERRUPTED, JobAttempt.SUCCEEDED);
    }

    @Test
    void renewedLeaseIsNotRecoveredWhileAnExpiredLeaseIs() {
        long accountId = createAccount();
        BackgroundJobRef activeRef = request(accountId, 205L);
        ClaimOutcome active = jobs.claim(activeRef.jobId());
        assertThat(jobs.renewLease(activeRef.jobId(), active.attemptNumber())).isTrue();
        assertThat(recoveryService.recover(activeRef.jobId(), LocalDateTime.now()))
            .isEqualTo(BackgroundJobRecoveryService.RecoveryOutcome.NOT_STALE);
        assertThat(stored(activeRef.jobId()).getStatus()).isEqualTo(BackgroundJob.RUNNING);

        BackgroundJobRef expiredRef = request(accountId, 206L);
        ClaimOutcome expired = jobs.claim(expiredRef.jobId());
        BackgroundJob expiredJob = stored(expiredRef.jobId());
        expiredJob.setLeaseExpiresAt(LocalDateTime.now().minusSeconds(1));
        jobMapper.updateById(expiredJob);

        assertThat(recoveryService.recover(expiredRef.jobId(), LocalDateTime.now()))
            .isEqualTo(BackgroundJobRecoveryService.RecoveryOutcome.RETRY_SCHEDULED);
        assertThat(stored(expiredRef.jobId()).getStatus()).isEqualTo(BackgroundJob.PENDING);
        assertThat(attempts(expiredRef.jobId()).getFirst().getStatus()).isEqualTo(JobAttempt.INTERRUPTED);
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

    private BackgroundJobRef request(long accountId, long subjectId) {
        return jobs.request(new BackgroundJobRequest(
            "report.generate", accountId, subjectId,
            "report.generate:test:" + System.nanoTime(), "{}"));
    }

    private BackgroundJob stored(String jobId) {
        return jobMapper.selectOne(new LambdaQueryWrapper<BackgroundJob>()
            .eq(BackgroundJob::getJobId, jobId)
            .last("LIMIT 1"));
    }

    private List<JobAttempt> attempts(String jobId) {
        return attemptMapper.selectList(new LambdaQueryWrapper<JobAttempt>()
            .eq(JobAttempt::getJobId, jobId)
            .orderByAsc(JobAttempt::getAttemptNumber));
    }

    private long createAccount() {
        com.prelude.identity.Account account = new com.prelude.identity.Account();
        account.setUsername("jobs-race-" + System.nanoTime());
        account.setRevision(0L);
        accountMapper.insert(account);
        return account.getId();
    }
}
