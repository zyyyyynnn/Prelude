package com.interview.interview.application;

import com.interview.platform.realtime.RealtimePort;
import com.interview.platform.realtime.SseSessionStream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
@RequiredArgsConstructor
public class ListenInterview {

    private static final long SSE_TIMEOUT_MS = 180000L;

    private final InterviewSessionAccess sessionAccess;
    private final RealtimePort realtimePort;

    public SseEmitter execute(Long sessionId) {
        sessionAccess.requireOwned(sessionId, sessionAccess.currentUserId());

        SseSessionStream stream = SseSessionStream.open(realtimePort, sessionId, SSE_TIMEOUT_MS);
        try {
            stream.send("ping", "connected");
        } catch (RuntimeException exception) {
            stream.complete();
        }
        return stream.emitter();
    }
}
