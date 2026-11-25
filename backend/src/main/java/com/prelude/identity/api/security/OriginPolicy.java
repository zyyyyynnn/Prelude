package com.prelude.identity.api.security;

import java.util.List;

public interface OriginPolicy {

    List<String> allowedOrigins();

    boolean isAllowed(String origin);

    boolean isAllowed(String origin, RequestOrigin requestOrigin);

    record RequestOrigin(String scheme, String host, int port) {
    }
}
