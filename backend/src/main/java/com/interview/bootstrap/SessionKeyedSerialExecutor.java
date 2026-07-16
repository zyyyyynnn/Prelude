package com.interview.bootstrap;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Runs tasks in FIFO order per session while allowing different sessions to execute in parallel.
 */
public final class SessionKeyedSerialExecutor implements Executor {

    private final Executor delegate;
    private final ConcurrentHashMap<Long, SerialLane> lanes = new ConcurrentHashMap<>();

    public SessionKeyedSerialExecutor(Executor delegate) {
        this.delegate = delegate;
    }

    public void executeForSession(Long sessionId, Runnable command) {
        if (sessionId == null) {
            delegate.execute(command);
            return;
        }
        lanes.computeIfAbsent(sessionId, ignored -> new SerialLane(delegate)).enqueue(command);
    }

    @Override
    public void execute(Runnable command) {
        delegate.execute(command);
    }

    private static final class SerialLane {
        private final Executor delegate;
        private final Deque<Runnable> queue = new ArrayDeque<>();
        private final AtomicBoolean draining = new AtomicBoolean(false);

        private SerialLane(Executor delegate) {
            this.delegate = delegate;
        }

        private void enqueue(Runnable command) {
            synchronized (queue) {
                queue.addLast(command);
                if (!draining.get()) {
                    draining.set(true);
                    delegate.execute(this::drainNext);
                }
            }
        }

        private void drainNext() {
            Runnable next;
            synchronized (queue) {
                next = queue.pollFirst();
                if (next == null) {
                    draining.set(false);
                    return;
                }
            }
            try {
                next.run();
            } finally {
                synchronized (queue) {
                    if (queue.isEmpty()) {
                        draining.set(false);
                    } else {
                        delegate.execute(this::drainNext);
                    }
                }
            }
        }
    }
}
