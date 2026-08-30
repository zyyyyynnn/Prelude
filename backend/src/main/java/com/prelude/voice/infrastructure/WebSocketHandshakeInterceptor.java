package com.prelude.voice.infrastructure;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

@Slf4j
@Component
public class WebSocketHandshakeInterceptor implements HandshakeInterceptor {

    @Override
    public boolean beforeHandshake(
        ServerHttpRequest request,
        ServerHttpResponse response,
        WebSocketHandler wsHandler,
        Map<String, Object> attributes
    ) throws Exception {
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

    @Override
    public void afterHandshake(
        ServerHttpRequest request,
        ServerHttpResponse response,
        WebSocketHandler wsHandler,
        Exception exception
    ) {
    }
}
