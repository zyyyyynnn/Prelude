package com.prelude.voice.infrastructure;

import com.prelude.identity.api.security.OriginPolicy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketHandshakeInterceptor implements HandshakeInterceptor {

    private final OriginPolicy originPolicy;

    @Override
    public boolean beforeHandshake(
        ServerHttpRequest request,
        ServerHttpResponse response,
        WebSocketHandler wsHandler,
        Map<String, Object> attributes
    ) throws Exception {
        if (!isOriginAllowed(request)) {
            response.setStatusCode(HttpStatus.FORBIDDEN);
            log.warn("WebSocket handshake rejected: origin is not allowed");
            return false;
        }
        if (request instanceof ServletServerHttpRequest servletRequest) {
            jakarta.servlet.http.HttpSession httpSession =
                servletRequest.getServletRequest().getSession(false);
            Object principal = servletRequest.getServletRequest().getUserPrincipal() == null
                ? null
                : servletRequest.getServletRequest().getUserPrincipal().getName();
            if (principal != null && httpSession != null) {
                try {
                    attributes.put("accountId", Long.valueOf(principal.toString()));
                    attributes.put("authSessionId", httpSession.getId());
                    return true;
                } catch (NumberFormatException exception) {
                    log.warn("WebSocket handshake rejected: invalid session principal");
                }
            }
        }
        log.warn("WebSocket handshake rejected: missing authenticated session");
        return false;
    }

    private boolean isOriginAllowed(ServerHttpRequest request) {
        String origin = request.getHeaders().getOrigin();
        if (origin == null) {
            return true;
        }
        if (request instanceof ServletServerHttpRequest servletRequest) {
            var servlet = servletRequest.getServletRequest();
            return originPolicy.isAllowed(origin, new OriginPolicy.RequestOrigin(
                servlet.getScheme(), servlet.getServerName(), servlet.getServerPort()));
        }
        var uri = request.getURI();
        String scheme = "ws".equals(uri.getScheme()) ? "http"
            : "wss".equals(uri.getScheme()) ? "https" : uri.getScheme();
        return originPolicy.isAllowed(origin, new OriginPolicy.RequestOrigin(scheme, uri.getHost(), uri.getPort()));
    }

    @Override
    public void afterHandshake(
        ServerHttpRequest request,
        ServerHttpResponse response,
        WebSocketHandler wsHandler,
        Exception exception
    ) {
    }
}
