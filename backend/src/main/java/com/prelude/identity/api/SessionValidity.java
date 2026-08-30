package com.prelude.identity.api;

/**
 * Session validity contract for long-lived connections (WebSocket, SSE).
 * A connection is authorized only while the originating HTTP session still
 * exists in the session store AND its security context carries the given
 * account — checking the session id alone is not enough.
 */
public interface SessionValidity {

    boolean isActive(String sessionId, long accountId);
}
