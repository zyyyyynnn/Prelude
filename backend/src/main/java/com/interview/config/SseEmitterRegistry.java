package com.interview.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Slf4j
@Component
public class SseEmitterRegistry {

    private final Map<Long, List<SseEmitter>> emitters = new ConcurrentHashMap<>();

    public void register(Long sessionId, SseEmitter emitter) {
        emitters.computeIfAbsent(sessionId, k -> new CopyOnWriteArrayList<>()).add(emitter);
        log.info("Registered SSE emitter for session {}. Active emitters: {}", sessionId, emitters.get(sessionId).size());

        emitter.onCompletion(() -> remove(sessionId, emitter));
        emitter.onTimeout(() -> remove(sessionId, emitter));
        emitter.onError(e -> remove(sessionId, emitter));
    }

    public void remove(Long sessionId, SseEmitter emitter) {
        List<SseEmitter> list = emitters.get(sessionId);
        if (list != null) {
            list.remove(emitter);
            if (list.isEmpty()) {
                emitters.remove(sessionId);
            }
            log.info("Removed SSE emitter for session {}. Remaining active: {}", sessionId, list.size());
        }
    }

    public void broadcast(Long sessionId, String eventName, String data) {
        List<SseEmitter> list = emitters.get(sessionId);
        if (list == null || list.isEmpty()) {
            log.info("No active SSE emitters registered for session {} to broadcast event '{}'", sessionId, eventName);
            return;
        }

        log.info("Broadcasting event '{}' to {} SSE emitters for session {}", eventName, list.size(), sessionId);
        List<SseEmitter> deadEmitters = new ArrayList<>();
        for (SseEmitter emitter : list) {
            try {
                emitter.send(SseEmitter.event().name(eventName).data(data));
            } catch (IOException e) {
                log.debug("Failed to send SSE event to emitter for session {}", sessionId);
                deadEmitters.add(emitter);
            }
        }

        for (SseEmitter dead : deadEmitters) {
            remove(sessionId, dead);
        }
    }
}
