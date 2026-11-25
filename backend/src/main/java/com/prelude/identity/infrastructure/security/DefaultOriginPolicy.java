package com.prelude.identity.infrastructure.security;

import com.prelude.identity.api.security.OriginPolicy;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Component
class DefaultOriginPolicy implements OriginPolicy {

    private static final Map<String, Integer> DEFAULT_PORTS = Map.of("http", 80, "https", 443);
    private static final Set<String> SCHEMES = Set.of("http", "https");

    private final List<String> allowedOrigins;

    DefaultOriginPolicy(CorsProperties properties) {
        this.allowedOrigins = properties.allowedOrigins();
        this.allowedOrigins.forEach(DefaultOriginPolicy::requireConfiguredOrigin);
    }

    @Override
    public List<String> allowedOrigins() {
        return allowedOrigins;
    }

    @Override
    public boolean isAllowed(String origin) {
        return origin != null && allowedOrigins.contains(origin);
    }

    @Override
    public boolean isAllowed(String origin, RequestOrigin requestOrigin) {
        if (isAllowed(origin)) {
            return true;
        }
        Origin candidate = parseOrigin(origin);
        return candidate != null
            && requestOrigin != null
            && SCHEMES.contains(normalizeScheme(requestOrigin.scheme()))
            && candidate.scheme().equals(normalizeScheme(requestOrigin.scheme()))
            && candidate.host().equalsIgnoreCase(requestOrigin.host())
            && candidate.port() == normalizePort(requestOrigin.scheme(), requestOrigin.port());
    }

    private static void requireConfiguredOrigin(String value) {
        if ("*".equals(value) || parseOrigin(value) == null) {
            throw new IllegalStateException("app.cors.allowed-origins must contain explicit HTTP(S) origins");
        }
    }

    private static Origin parseOrigin(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            URI uri = URI.create(value);
            String scheme = normalizeScheme(uri.getScheme());
            if (!SCHEMES.contains(scheme)
                || uri.getHost() == null
                || uri.getRawUserInfo() != null
                || uri.getRawQuery() != null
                || uri.getRawFragment() != null
                || (uri.getRawPath() != null && !uri.getRawPath().isEmpty() && !"/".equals(uri.getRawPath()))
                || uri.getPort() > 65535) {
                return null;
            }
            return new Origin(scheme, uri.getHost(), normalizePort(scheme, uri.getPort()));
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private static String normalizeScheme(String scheme) {
        return scheme == null ? "" : scheme.toLowerCase(Locale.ROOT);
    }

    private static int normalizePort(String scheme, int port) {
        if (port >= 0) {
            return port;
        }
        return DEFAULT_PORTS.getOrDefault(normalizeScheme(scheme), -1);
    }

    private record Origin(String scheme, String host, int port) {
    }
}
