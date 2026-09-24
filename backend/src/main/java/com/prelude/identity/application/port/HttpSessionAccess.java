package com.prelude.identity.application.port;

/**
 * The calling HTTP session, reduced to what a use case actually needs.
 *
 * <p>Use cases must not name {@code jakarta.servlet.http.HttpSession}: doing so makes
 * them HTTP adapters and leaves no seam to test the policy through. The web layer
 * adapts the real session to this port.
 */
public interface HttpSessionAccess {

    /** The session id, or null when there is no session. */
    String currentSessionId();

    /** Reads one attribute, or null when absent. */
    Object attribute(String name);

    /** Writes one attribute. */
    void attribute(String name, Object value);

    /** Drops one attribute; absent is not an error. */
    void removeAttribute(String name);
}
