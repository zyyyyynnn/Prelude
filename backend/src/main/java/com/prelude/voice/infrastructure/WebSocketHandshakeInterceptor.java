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
            Object principal = servletRequest.getServletRequest().getUserPrincipal() == null
                ? null
                : servletRequest.getServletRequest().getUserPrincipal().getName();
            if (principal != null) {
                try {
                    attributes.put("userId", Long.valueOf(principal.toString()));
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
