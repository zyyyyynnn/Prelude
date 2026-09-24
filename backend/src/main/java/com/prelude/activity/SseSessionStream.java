package com.prelude.activity;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * One server-sent-events connection plus everything that keeps it honest: registration in
 * the realtime hub, a heartbeat so idle proxies do not drop a long turn, and a single owner
 * for the emitter lifecycle callbacks.
 *
 * <p>Callers must not touch {@link #emitter()}'s {@code onTimeout}/{@code onError}/
 * {@code onCompletion}: those are set here once, and each is a setter, so assigning them
 * elsewhere silently drops the heartbeat shutdown and the hub unregistration.
 */
public final class SseSessionStream {

    /**
     * A turn can occupy the LLM call timeout once per transport attempt, so this is a ceiling
     * well past that worst case rather than an idle timeout — liveness is the heartbeat's job.
     */
    private static final long TIMEOUT_MS = 300_000L;
    private static final long HEARTBEAT_INTERVAL_MS = 30_000L;

    /** What the client is told when the ceiling above expires. */
    private static final String TIMEOUT_MESSAGE = "连接超时，请重试";

    private final SseEmitter emitter;
    private final RealtimeConnection connection;
    private final ScheduledFuture<?> heartbeat;

    private SseSessionStream(
        SseEmitter emitter,
        RealtimeConnection connection,
        ScheduledFuture<?> heartbeat
    ) {
        this.emitter = emitter;
        this.connection = connection;
        this.heartbeat = heartbeat;
    }

    public static SseSessionStream open(
        RealtimePort realtimePort,
        Long sessionId,
        ScheduledExecutorService heartbeatExecutor
    ) {
        SseEmitter emitter = new SseEmitter(TIMEOUT_MS);
        String connectionId = UUID.randomUUID().toString();
        SessionStreamSink sink = new SessionStreamSink() {
            @Override
            public void send(String eventName, Object payload) {
                try {
                    emitter.send(SseEmitter.event().name(eventName).data(payload));
                } catch (IOException error) {
                    throw new RealtimeDeliveryException(error);
                }
            }

            @Override
            public void complete() {
                emitter.complete();
            }
        };
        RealtimeConnection connection = realtimePort.register(sessionId, connectionId, sink);
        ScheduledFuture<?> heartbeat = heartbeatExecutor.scheduleAtFixedRate(
            () -> {
                try {
                    connection.send("ping", "heartbeat");
                } catch (RuntimeException error) {
                    emitter.completeWithError(error);
                }
            },
            HEARTBEAT_INTERVAL_MS,
            HEARTBEAT_INTERVAL_MS,
            TimeUnit.MILLISECONDS);
        Runnable shutdown = () -> {
            heartbeat.cancel(false);
            realtimePort.unregister(sessionId, connectionId);
        };
        SseSessionStream stream = new SseSessionStream(emitter, connection, heartbeat);
        emitter.onCompletion(shutdown);
        emitter.onTimeout(() -> {
            shutdown.run();
            stream.completeWithError(TIMEOUT_MESSAGE);
        });
        emitter.onError(error -> shutdown.run());
        return stream;
    }

    public SseEmitter emitter() {
        return emitter;
    }

    public void send(String eventName, Object payload) {
        connection.send(eventName, payload);
    }

    public void complete() {
        heartbeat.cancel(false);
        connection.complete();
    }

    /** Tells the client why the stream ended, then closes it. */
    public void completeWithError(String message) {
        try {
            connection.send("error", message);
        } catch (RuntimeException ignored) {
            // Connection may already be closed.
        } finally {
            complete();
        }
    }

    private static final class RealtimeDeliveryException extends RuntimeException {
        private RealtimeDeliveryException(IOException cause) {
            super(cause);
        }
    }
}
