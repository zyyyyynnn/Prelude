package com.prelude.interview.application;

import com.prelude.BusinessException;
import com.prelude.activity.RealtimePort;
import com.prelude.activity.SseSessionStream;
import com.prelude.identity.api.SessionValidity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
@RequiredArgsConstructor
public class ListenInterview {

    private static final long SSE_TIMEOUT_MS = 180000L;

    private final InterviewSessionAccess sessionAccess;
    private final RealtimePort realtimePort;
    private final SessionValidity sessionValidity;

    public SseEmitter execute(Long sessionId, String authSessionId) {
        long accountId = sessionAccess.currentAccountId();
        if (!sessionValidity.isActive(authSessionId, accountId)) {
            throw BusinessException.unauthorized("登录已失效，请重新登录");
        }
        sessionAccess.requireOwned(sessionId, accountId);

        SseSessionStream stream = SseSessionStream.open(realtimePort, sessionId, SSE_TIMEOUT_MS);
        try {
            stream.send("ping", "connected");
        } catch (RuntimeException exception) {
            stream.complete();
        }
        return stream.emitter();
    }
}
