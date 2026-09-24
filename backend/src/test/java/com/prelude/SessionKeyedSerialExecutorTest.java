package com.prelude;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Per-session FIFO with cross-session parallelism: two stops from one interview never
 * interleave, while different interviews stay free to run together.
 */
class SessionKeyedSerialExecutorTest {

    private final ExecutorService pool = Executors.newFixedThreadPool(4);
    private final SessionKeyedSerialExecutor executor = new SessionKeyedSerialExecutor(pool);

    @AfterEach
    void shutdown() {
        pool.shutdownNow();
    }

    @Test
    void tasksFromOneSessionRunInSubmissionOrder() throws Exception {
        List<Integer> order = new ArrayList<>();
        CountDownLatch done = new CountDownLatch(3);
        for (int index = 0; index < 3; index++) {
            int captured = index;
            executor.executeForSession(7L, () -> {
                order.add(captured);
                done.countDown();
            });
        }

        assertThat(done.await(2, TimeUnit.SECONDS)).isTrue();
        assertThat(order).containsExactly(0, 1, 2);
    }

    @Test
    void oneSessionNeverRunsTwoTasksAtOnce() throws Exception {
        AtomicInteger concurrent = new AtomicInteger();
        AtomicInteger maxConcurrent = new AtomicInteger();
        CountDownLatch done = new CountDownLatch(8);
        for (int index = 0; index < 8; index++) {
            executor.executeForSession(7L, () -> {
                int active = concurrent.incrementAndGet();
                maxConcurrent.accumulateAndGet(active, Math::max);
                try {
                    Thread.sleep(5);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                } finally {
                    concurrent.decrementAndGet();
                    done.countDown();
                }
            });
        }

        assertThat(done.await(2, TimeUnit.SECONDS)).isTrue();
        assertThat(maxConcurrent).hasValue(1);
    }

    @Test
    void differentSessionsMayRunTogether() throws Exception {
        CountDownLatch bothRunning = new CountDownLatch(2);
        CountDownLatch release = new CountDownLatch(1);
        Runnable hold = () -> {
            bothRunning.countDown();
            try {
                release.await(2, TimeUnit.SECONDS);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
        };
        executor.executeForSession(1L, hold);
        executor.executeForSession(2L, hold);

        assertThat(bothRunning.await(2, TimeUnit.SECONDS)).isTrue();
        release.countDown();
    }

    @Test
    void aMissingSessionIdGoesStraightToTheDelegate() throws Exception {
        List<String> order = new ArrayList<>();
        CountDownLatch done = new CountDownLatch(1);
        executor.executeForSession(null, () -> {
            order.add("ran");
            done.countDown();
        });

        assertThat(done.await(2, TimeUnit.SECONDS)).isTrue();
        assertThat(order).containsExactly("ran");
    }

    @Test
    void plainExecuteDoesNotQueueOnASessionLane() throws Exception {
        List<String> order = new ArrayList<>();
        CountDownLatch done = new CountDownLatch(1);
        executor.execute(() -> {
            order.add("ran");
            done.countDown();
        });

        assertThat(done.await(2, TimeUnit.SECONDS)).isTrue();
        assertThat(order).containsExactly("ran");
    }
}
