package com.prelude.identity.api;

import java.time.Instant;

public record SessionView(
    String sessionId,
    boolean current,
    Instant lastAccessedAt
) {
}
