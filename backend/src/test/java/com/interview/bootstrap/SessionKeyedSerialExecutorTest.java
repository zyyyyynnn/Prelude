package com.interview.bootstrap;

import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class SessionKeyedSerialExecutorTest {

    @Test
    void keepsTasksInOrderWithinTheSameSession() throws Exception {
        SessionKeyedSerialExecutor executor = executor(2);
        List<String> order = Collections.synchronizedList(new ArrayList<>());
        CountDownLatch finished = new CountDownLatch(3);

        for (int index = 0; index < 3; index++) {
            int taskIndex = index;
            executor.executeForSession(7L, () -> {
                order.add("task-" + taskIndex);
                finished.countDown();
            });
        }

        assertThat(finished.await(2, TimeUnit.SECONDS)).isTrue();
        assertThat(order).containsExactly("task-0", "task-1", "task-2");
    }

    @Test
    void allowsDifferentSessionsToProgressInParallel() throws Exception {
        SessionKeyedSerialExecutor executor = executor(2);
        CountDownLatch firstSessionStarted = new CountDownLatch(1);
        CountDownLatch releaseFirstSession = new CountDownLatch(1);
        CountDownLatch secondSessionFinished = new CountDownLatch(1);

        executor.executeForSession(1L, () -> {
            firstSessionStarted.countDown();
            awaitQuietly(releaseFirstSession);
        });
        executor.executeForSession(2L, () -> secondSessionFinished.countDown());

        assertThat(firstSessionStarted.await(2, TimeUnit.SECONDS)).isTrue();
        assertThat(secondSessionFinished.await(2, TimeUnit.SECONDS)).isTrue();
        releaseFirstSession.countDown();
    }

    private static SessionKeyedSerialExecutor executor(int poolSize) {
        ThreadPoolTaskExecutor delegate = new ThreadPoolTaskExecutor();
        delegate.setCorePoolSize(poolSize);
        delegate.setMaxPoolSize(poolSize);
        delegate.setQueueCapacity(16);
        delegate.setThreadNamePrefix("session-tts-test-");
        delegate.initialize();
        return new SessionKeyedSerialExecutor(delegate);
    }

    private static void awaitQuietly(CountDownLatch latch) {
        try {
            assertThat(latch.await(2, TimeUnit.SECONDS)).isTrue();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new AssertionError(interrupted);
        }
    }
}
