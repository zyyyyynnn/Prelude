package com.prelude;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.net.URI;
import java.util.Map;
import java.util.Set;

/**
 * Rejects unsafe browser requests whose Origin is neither an allowed origin nor the same origin.
 */
public class OriginValidationFilter extends OncePerRequestFilter {

    private static final Set<String> UNSAFE_METHODS = Set.of("POST", "PUT", "PATCH", "DELETE");
    private static final Map<String, Integer> DEFAULT_PORTS = Map.of("http", 80, "https", 443);

    private final CorsProperties corsProperties;

    public OriginValidationFilter(CorsProperties corsProperties) {
        this.corsProperties = corsProperties;
    }

    @Override
    protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain filterChain
    ) throws ServletException, IOException {
        String origin = request.getHeader("Origin");
        if (origin != null && UNSAFE_METHODS.contains(request.getMethod()) && !isAllowed(origin, request)) {
            SecurityProblemWriter.write(response, HttpServletResponse.SC_FORBIDDEN, "origin_rejected", "请求来源不被允许");
            return;
        }
        filterChain.doFilter(request, response);
    }

    private boolean isAllowed(String origin, HttpServletRequest request) {
        if (corsProperties.allowedOrigins() != null && corsProperties.allowedOrigins().contains(origin)) {
            return true;
        }
        return isSameOrigin(origin, request);
    }

    private boolean isSameOrigin(String origin, HttpServletRequest request) {
        try {
            URI originUri = URI.create(origin);
            if (originUri.getHost() == null) {
                return false;
            }
            int originPort = originUri.getPort() >= 0
                ? originUri.getPort()
                : DEFAULT_PORTS.getOrDefault(originUri.getScheme(), -1);
            int serverPort = request.getServerPort() >= 0 ? request.getServerPort() : -1;
            int requestPort = isDefaultPort(request.getScheme(), serverPort)
                ? DEFAULT_PORTS.getOrDefault(request.getScheme(), -1)
                : serverPort;
            return originUri.getScheme().equalsIgnoreCase(request.getScheme())
                && originUri.getHost().equalsIgnoreCase(request.getServerName())
                && originPort == requestPort;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private boolean isDefaultPort(String scheme, int port) {
        return port == DEFAULT_PORTS.getOrDefault(scheme, -1);
    }
}
