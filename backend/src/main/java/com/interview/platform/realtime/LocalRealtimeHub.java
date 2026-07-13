package com.interview.platform.realtime;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@ConditionalOnProperty(prefix = "prelude.realtime", name = "mode", havingValue = "local", matchIfMissing = true)
public class LocalRealtimeHub implements RealtimePort {

    private final Map<Long, Map<String, SessionStreamSink>> connections = new ConcurrentHashMap<>();

    @Override
    public RealtimeConnection register(Long sessionId, String connectionId, SessionStreamSink sink) {
        connections.computeIfAbsent(sessionId, ignored -> new ConcurrentHashMap<>()).put(connectionId, sink);
        log.info("Registered realtime connection {} for session {}", connectionId, sessionId);
        return new LocalConnection(sessionId, connectionId, sink);
    }

    @Override
    public void unregister(Long sessionId, String connectionId) {
        Map<String, SessionStreamSink> sessionConnections = connections.get(sessionId);
        if (sessionConnections == null) {
            return;
        }
        sessionConnections.remove(connectionId);
        if (sessionConnections.isEmpty()) {
            connections.remove(sessionId, sessionConnections);
        }
    }

    @Override
    public void publish(Long sessionId, String eventName, Object payload) {
        Map<String, SessionStreamSink> sessionConnections = connections.get(sessionId);
        if (sessionConnections == null || sessionConnections.isEmpty()) {
            log.info("No realtime connections for session {} and event '{}'", sessionId, eventName);
            return;
        }
        for (var entry : new ArrayList<>(sessionConnections.entrySet())) {
            try {
                entry.getValue().send(eventName, payload);
            } catch (RuntimeException deliveryFailure) {
                unregister(sessionId, entry.getKey());
            }
        }
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
