package com.interview.platform.realtime;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "prelude.realtime", name = "mode", havingValue = "local", matchIfMissing = true)
public class LocalRealtimeHub implements RealtimePort {

    private final RealtimeConnectionRegistry registry = new RealtimeConnectionRegistry();

    @Override
    public RealtimeConnection register(Long sessionId, String connectionId, SessionStreamSink sink) {
        registry.register(sessionId, connectionId, sink);
        return new LocalConnection(sessionId, connectionId, sink);
    }

    @Override
    public void unregister(Long sessionId, String connectionId) {
        registry.unregister(sessionId, connectionId);
    }

    @Override
    public void publish(Long sessionId, String eventName, Object payload) {
        registry.publish(sessionId, eventName, payload);
    }

    private final class LocalConnection implements RealtimeConnection {
        private final Long sessionId;
        private final String connectionId;
        private final SessionStreamSink sink;

        private LocalConnection(Long sessionId, String connectionId, SessionStreamSink sink) {
            this.sessionId = sessionId;
            this.connectionId = connectionId;
            this.sink = sink;
        }

        @Override
        public String connectionId() {
            return connectionId;
        }

        @Override
        public void send(String eventName, Object payload) {
            sink.send(eventName, payload);
        }

        @Override
        public void complete() {
            try {
                sink.complete();
            } finally {
                unregister(sessionId, connectionId);
            }
        }
    }
}
