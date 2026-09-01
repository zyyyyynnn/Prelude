package com.prelude.jobs;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.prelude.jobs.integration.BackgroundJobOperations;
import com.prelude.jobs.integration.BackgroundJobOperations.BackgroundJobRef;
import com.prelude.jobs.integration.BackgroundJobOperations.BackgroundJobRequest;
import com.prelude.jobs.integration.BackgroundJobOperations.ClaimOutcome;
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

        Future<Boolean> cancel = executor.submit(raced(start, () -> jobs.cancel(ref.jobId(), accountId)));
        Future<ClaimOutcome> claim = executor.submit(raced(start, () -> jobs.claim(ref.jobId())));

        boolean cancelled = cancel.get();
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
        assertThat(jobs.claim(ref.jobId()).claimed()).isTrue();
        BackgroundJob job = stored(ref.jobId());
        job.setClaimedAt(LocalDateTime.now().minusHours(1));
        jobMapper.updateById(job);
        LocalDateTime staleBefore = LocalDateTime.now().minusMinutes(5);
        CyclicBarrier start = new CyclicBarrier(2);

        Future<Void> complete = executor.submit(raced(start, () -> {
            jobs.complete(ref.jobId());
            return null;
        }));
        Future<BackgroundJobRecoveryService.RecoveryOutcome> recover = executor.submit(
            raced(start, () -> recoveryService.recover(ref.jobId(), staleBefore)));
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
