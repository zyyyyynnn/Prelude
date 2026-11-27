package com.prelude;

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
        SerialLane lane = lanes.computeIfAbsent(sessionId, ignored -> new SerialLane(delegate, sessionId));
        if (!lane.enqueue(command)) {
            lanes.remove(sessionId, lane);
            executeForSession(sessionId, command);
        }
    }

    @Override
    public void execute(Runnable command) {
        delegate.execute(command);
    }

    int laneCount() {
        return lanes.size();
    }

    private final class SerialLane {
        private final Long sessionId;
        private final Executor delegate;
        private final Deque<Runnable> queue = new ArrayDeque<>();
        private final AtomicBoolean draining = new AtomicBoolean(false);
        private boolean retired;

        private SerialLane(Executor delegate, Long sessionId) {
            this.delegate = delegate;
            this.sessionId = sessionId;
        }

        private boolean enqueue(Runnable command) {
            synchronized (queue) {
                if (retired) {
                    return false;
                }
                queue.addLast(command);
                if (!draining.get()) {
                    draining.set(true);
                    delegate.execute(this::drainNext);
                }
                return true;
            }
        }

        private void drainNext() {
            Runnable next;
            synchronized (queue) {
                next = queue.pollFirst();
                if (next == null) {
                    draining.set(false);
                    retired = true;
                    return;
                }
            }
            try {
                next.run();
            } finally {
                synchronized (queue) {
                    if (queue.isEmpty()) {
                        draining.set(false);
                        retired = true;
                        lanes.remove(sessionId, this);
                    } else {
                        delegate.execute(this::drainNext);
                    }
                }
            }
        }
    }
}
