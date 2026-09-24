package com.prelude.interview.application;

import com.prelude.BusinessException;
import com.prelude.activity.SseSessionStream;
import com.prelude.identity.api.SessionValidity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Opens the server-sent-events channel for an interview. The caller owns the stream
 * lifecycle; this use case only authorizes the listener and announces the connection.
 */
@Service
@RequiredArgsConstructor
public class ListenInterview {

    private final InterviewSessionAccess sessionAccess;
    private final SessionValidity sessionValidity;

    public void execute(Long sessionId, String authSessionId, SseSessionStream stream) {
        long accountId = sessionAccess.currentAccountId();
        if (!sessionValidity.isActive(authSessionId, accountId)) {
            throw BusinessException.unauthorized("登录已失效，请重新登录");
        }
        sessionAccess.requireOwned(sessionId, accountId);

        try {
            stream.send("ping", "connected");
        } catch (RuntimeException exception) {
            stream.complete();
        }
    }
}
