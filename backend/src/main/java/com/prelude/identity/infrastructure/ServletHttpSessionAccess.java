package com.prelude.identity.infrastructure;

import com.prelude.identity.application.port.HttpSessionAccess;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Adapts the calling HTTP session to the port use cases depend on, so no use case
 * names {@link HttpSession}.
 */
@Component
@RequiredArgsConstructor
public class ServletHttpSessionAccess implements HttpSessionAccess {

    private final HttpSession session;

    @Override
    public String currentSessionId() {
        return session == null ? null : session.getId();
    }

    @Override
    public Object attribute(String name) {
        return session == null ? null : session.getAttribute(name);
    }

    @Override
    public void attribute(String name, Object value) {
        if (session != null) {
            session.setAttribute(name, value);
        }
    }

    @Override
    public void removeAttribute(String name) {
        if (session != null) {
            session.removeAttribute(name);
        }
    }
}
