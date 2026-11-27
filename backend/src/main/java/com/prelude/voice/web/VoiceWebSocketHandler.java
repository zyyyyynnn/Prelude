package com.prelude.voice.web;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import com.prelude.interview.application.port.InterviewSessionGuard;
import com.prelude.voice.application.VoiceInterviewTurnService;
import com.prelude.voice.application.VoiceTurnEventSink;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.AbstractWebSocketHandler;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@RequiredArgsConstructor
public class VoiceWebSocketHandler extends AbstractWebSocketHandler {

    private static final int MAX_BUFFERED_AUDIO_BYTES = 20 * 1024 * 1024;
    private static final int SEND_TIME_LIMIT_MS = 10_000;
    private static final int SEND_BUFFER_LIMIT_BYTES = 4 * 1024 * 1024;

    private final ObjectMapper objectMapper;
    private final VoiceInterviewTurnService voiceInterviewTurnService;
    private final InterviewSessionGuard interviewSessionGuard;
    private final com.prelude.identity.api.SessionValidity sessionValidity;

    private final Map<String, VoiceConnection> connections = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        Long accountId = (Long) session.getAttributes().get("accountId");
        if (accountId == null) {
            log.warn("WebSocket connection rejected: accountId not bound in session attributes");
            session.close(CloseStatus.BAD_DATA);
            return;
        }
        connections.put(session.getId(), new VoiceConnection(
            new ConcurrentWebSocketSessionDecorator(session, SEND_TIME_LIMIT_MS, SEND_BUFFER_LIMIT_BYTES),
            accountId,
            (String) session.getAttributes().get("authSessionId")
        ));
        log.info("WebSocket connection established for account {}, connection id: {}", accountId, session.getId());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        VoiceConnection connection = connections.remove(session.getId());
        if (connection != null) {
            synchronized (connection) {
                connection.state = VoiceConnectionState.CLOSED;
                connection.generation++;
            }
        }
        log.info("WebSocket connection closed, connection id: {}", session.getId());
    }

    @Override
    protected void handleBinaryMessage(WebSocketSession session, BinaryMessage message) throws Exception {
        if (sessionRevoked(session)) {
            closeRevokedSession(session);
            return;
        }
        VoiceConnection connection = connections.get(session.getId());
        if (connection == null) {
            return;
        }
        synchronized (connection) {
            if (connection.state != VoiceConnectionState.RECORDING) {
                return;
            }
            ByteBuffer payload = message.getPayload();
            int incoming = payload.remaining();
            byte[] bytes = new byte[incoming];
            payload.get(bytes);
            if (connection.buffer.size() + incoming > MAX_BUFFERED_AUDIO_BYTES) {
                int discarded = connection.buffer.size() + incoming;
                connection.buffer.reset();
                log.warn("Discarding oversized audio buffer for connection {}, buffered {} bytes",
                    session.getId(), discarded);
                sendJson(connection, Map.of("type", "error", "message", "音频长度超出限制，请重新开始本轮"));
                return;
            }
            connection.buffer.write(bytes);
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        Long accountId = (Long) session.getAttributes().get("accountId");
        if (accountId == null) {
            session.close(CloseStatus.BAD_DATA);
            return;
        }
        if (sessionRevoked(session)) {
            closeRevokedSession(session);
            return;
        }
        VoiceConnection connection = connections.get(session.getId());
        if (connection == null) {
            return;
        }

        Map<String, Object> requestMap;
        try {
            requestMap = objectMapper.readValue(message.getPayload(), new TypeReference<Map<String, Object>>() {
            });
        } catch (Exception e) {
            log.warn("WebSocket parse text payload failed: {}", message.getPayload());
            return;
        }

        String type = (String) requestMap.get("type");
        synchronized (connection) {
            if ("start".equalsIgnoreCase(type)) {
                start(connection, accountId, requestMap);
            } else if ("stop".equalsIgnoreCase(type)) {
                stop(connection, accountId);
            }
        }
    }

    private void start(VoiceConnection connection, Long accountId, Map<String, Object> request) {
        if (connection.state == VoiceConnectionState.PROCESSING) {
            sendJson(connection, Map.of("type", "error", "message", "正在处理上一轮语音，请稍后再试"));
            return;
        }
        if (connection.state == VoiceConnectionState.CLOSED) {
            return;
        }
        Number sessionIdNum = (Number) request.get("sessionId");
        if (sessionIdNum == null) {
            return;
        }
        Long requestedSessionId = sessionIdNum.longValue();
        if (!interviewSessionGuard.isOngoing(accountId, requestedSessionId)) {
            sendJson(connection, Map.of("type", "error", "message", "面试会话不可用，请刷新后重试"));
            return;
        }
        connection.sessionId = requestedSessionId;
        connection.buffer.reset();
        connection.generation++;
        connection.state = VoiceConnectionState.RECORDING;
        log.info("Started voice session {} on connection {}", requestedSessionId, connection.session.getId());
    }

    private void stop(VoiceConnection connection, Long accountId) {
        if (connection.state != VoiceConnectionState.RECORDING || connection.sessionId == null) {
            sendJson(connection, Map.of("type", "error", "message", "面试会话未初始化"));
            return;
        }
        if (connection.buffer.size() == 0) {
            sendJson(connection, Map.of("type", "error", "message", "没有检测到任何音频数据"));
            return;
        }
        byte[] audioBytes = connection.buffer.toByteArray();
        connection.buffer.reset();
        long generation = ++connection.generation;
        Long sessionId = connection.sessionId;
        connection.state = VoiceConnectionState.PROCESSING;
        voiceInterviewTurnService.processTurn(
            accountId, sessionId, audioBytes, buildSink(connection, generation, sessionId));
    }

    private VoiceTurnEventSink buildSink(VoiceConnection connection, long generation, Long sessionId) {
        return new VoiceTurnEventSink() {
            @Override
            public void status(String status) {
                sendIfCurrent(connection, generation, sessionId, Map.of("type", "status", "status", status));
            }

            @Override
            public void userText(String text) {
                sendIfCurrent(connection, generation, sessionId, Map.of("type", "user_text", "text", text));
            }

            @Override
            public void assistantText(String chunk) {
                sendIfCurrent(connection, generation, sessionId, Map.of("type", "text", "chunk", chunk));
            }

            @Override
            public void audio(String base64Audio) {
                sendIfCurrent(connection, generation, sessionId, Map.of("type", "audio", "data", base64Audio));
            }

            @Override
            public void judge(int score, String hint) {
                sendIfCurrent(connection, generation, sessionId, Map.of("type", "judge", "score", score, "hint", hint));
            }

            @Override
            public void error(String message) {
                sendIfCurrent(connection, generation, sessionId, Map.of("type", "error", "message", message));
            }

            @Override
            public boolean isCurrent() {
                synchronized (connection) {
                    return connection.isCurrent(generation, sessionId);
                }
            }

            @Override
            public void invalidate() {
                synchronized (connection) {
                    if (connection.isCurrent(generation, sessionId)) {
                        connection.generation++;
                        connection.sessionId = null;
                        connection.state = VoiceConnectionState.IDLE;
                    }
                }
            }

            @Override
            public void turnFinished() {
                synchronized (connection) {
                    if (connection.isCurrent(generation, sessionId)) {
                        connection.generation++;
                        connection.state = VoiceConnectionState.RECORDING;
                    }
                }
            }
        };
    }

    private void sendIfCurrent(VoiceConnection connection, long generation, Long sessionId, Object payload) {
        synchronized (connection) {
            if (connection.isCurrent(generation, sessionId)) {
                sendJson(connection, payload);
            }
        }
    }

    private boolean sessionRevoked(WebSocketSession session) {
        Long accountId = (Long) session.getAttributes().get("accountId");
        String authSessionId = (String) session.getAttributes().get("authSessionId");
        if (accountId == null || authSessionId == null) {
            return true;
        }
        return !sessionValidity.isActive(authSessionId, accountId);
    }

    private void closeRevokedSession(WebSocketSession session) throws IOException {
        VoiceConnection connection = connections.remove(session.getId());
        if (connection != null) {
            synchronized (connection) {
                connection.state = VoiceConnectionState.CLOSED;
                connection.generation++;
            }
        }
        log.info("WebSocket closed: originating session revoked, connection id: {}", session.getId());
        session.close(CloseStatus.POLICY_VIOLATION.withReason("session revoked"));
    }

    private void sendJson(VoiceConnection connection, Object payload) {
        if (!connection.session.isOpen()) {
            return;
        }
        try {
            connection.session.sendMessage(new TextMessage(objectMapper.writeValueAsString(payload)));
        } catch (IOException e) {
            log.warn("Failed to push websocket message: {}", e.getMessage());
        }
    }

    private final class VoiceConnection {
        private final WebSocketSession session;
        private final Long accountId;
        private final String authSessionId;
        private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        private VoiceConnectionState state = VoiceConnectionState.IDLE;
        private Long sessionId;
        private long generation;

        private VoiceConnection(WebSocketSession session, Long accountId, String authSessionId) {
            this.session = session;
            this.accountId = accountId;
            this.authSessionId = authSessionId;
        }

        private boolean isCurrent(long generation, Long sessionId) {
            return state != VoiceConnectionState.CLOSED
                && this.generation == generation
                && Objects.equals(this.sessionId, sessionId)
                && sessionValidity.isActive(authSessionId, accountId);
        }
    }
}
