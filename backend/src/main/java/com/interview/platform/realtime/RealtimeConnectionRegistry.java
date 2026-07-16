package com.interview.platform.realtime;

import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
final class RealtimeConnectionRegistry {

    private final Map<Long, Map<String, SessionStreamSink>> connections = new ConcurrentHashMap<>();

    void register(Long sessionId, String connectionId, SessionStreamSink sink) {
        connections.computeIfAbsent(sessionId, ignored -> new ConcurrentHashMap<>()).put(connectionId, sink);
        log.info("Registered realtime connection {} for session {}", connectionId, sessionId);
    }

    void unregister(Long sessionId, String connectionId) {
        Map<String, SessionStreamSink> sessionConnections = connections.get(sessionId);
        if (sessionConnections == null) {
            return;
        }
        sessionConnections.remove(connectionId);
        if (sessionConnections.isEmpty()) {
            connections.remove(sessionId, sessionConnections);
        }
    }

    void publish(Long sessionId, String eventName, Object payload) {
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
}
