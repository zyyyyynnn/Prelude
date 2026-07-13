package com.interview.platform.realtime;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.UUID;

public final class SseSessionStream {

    private final SseEmitter emitter;
    private final RealtimeConnection connection;

    private SseSessionStream(SseEmitter emitter, RealtimeConnection connection) {
        this.emitter = emitter;
        this.connection = connection;
    }

    public static SseSessionStream open(RealtimePort realtimePort, Long sessionId, long timeoutMs) {
        SseEmitter emitter = new SseEmitter(timeoutMs);
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
        emitter.onCompletion(() -> realtimePort.unregister(sessionId, connectionId));
        emitter.onTimeout(() -> realtimePort.unregister(sessionId, connectionId));
        emitter.onError(error -> realtimePort.unregister(sessionId, connectionId));
        return new SseSessionStream(emitter, connection);
    }

    public SseEmitter emitter() {
        return emitter;
    }

    public void send(String eventName, Object payload) {
        connection.send(eventName, payload);
    }

    public void complete() {
        connection.complete();
    }

    private static final class RealtimeDeliveryException extends RuntimeException {
        private RealtimeDeliveryException(IOException cause) {
            super(cause);
        }
    }
}
