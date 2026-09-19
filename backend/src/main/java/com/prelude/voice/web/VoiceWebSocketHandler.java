package com.prelude.voice.web;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import com.prelude.interview.domain.InterviewSession;
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
import java.util.concurrent.ConcurrentHashMap;

/**
 * Voice WebSocket transport.
 *
 * Owns the connection lifecycle, the audio buffer, the active-session map,
 * and the JSON wire protocol. The actual turn processing (STT, LLM, TTS,
 * persistence, stage advance, judge, summary) is delegated to
 * {@link VoiceInterviewTurnService}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class VoiceWebSocketHandler extends AbstractWebSocketHandler {

    /** Roughly ten minutes of 16 kHz/16-bit mono audio; a longer client burst is a fault, not a turn. */
    private static final int MAX_BUFFERED_AUDIO_BYTES = 20 * 1024 * 1024;
    private static final int SEND_TIME_LIMIT_MS = 10_000;
    private static final int SEND_BUFFER_LIMIT_BYTES = 4 * 1024 * 1024;

    private final ObjectMapper objectMapper;
    private final VoiceInterviewTurnService voiceInterviewTurnService;
    private final com.prelude.identity.api.SessionValidity sessionValidity;

    private final Map<String, ByteArrayOutputStream> sessionBuffers = new ConcurrentHashMap<>();
    private final Map<String, Long> activeSessionIds = new ConcurrentHashMap<>();
    private final Map<String, WebSocketSession> outbound = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        Long accountId = (Long) session.getAttributes().get("accountId");
        if (accountId == null) {
            log.warn("WebSocket connection rejected: accountId not bound in session attributes");
            session.close(CloseStatus.BAD_DATA);
            return;
        }
        // Sink callbacks send from turn-processing threads while the container thread keeps
        // reading, and a raw WebSocketSession is not safe to write from two threads at once.
        outbound.put(session.getId(), new ConcurrentWebSocketSessionDecorator(
            session, SEND_TIME_LIMIT_MS, SEND_BUFFER_LIMIT_BYTES));
        sessionBuffers.put(session.getId(), new ByteArrayOutputStream());
        log.info("WebSocket connection established for account {}, connection id: {}", accountId, session.getId());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        sessionBuffers.remove(session.getId());
        activeSessionIds.remove(session.getId());
        outbound.remove(session.getId());
        log.info("WebSocket connection closed, connection id: {}", session.getId());
    }

    @Override
    protected void handleBinaryMessage(WebSocketSession session, BinaryMessage message) throws Exception {
        if (sessionRevoked(session)) {
            closeRevokedSession(session);
            return;
        }
        ByteArrayOutputStream buffer = sessionBuffers.get(session.getId());
        if (buffer == null) {
            return;
        }
        ByteBuffer payload = message.getPayload();
        int incoming = payload.remaining();
        byte[] bytes = new byte[incoming];
        payload.get(bytes);
        synchronized (buffer) {
            if (buffer.size() + incoming > MAX_BUFFERED_AUDIO_BYTES) {
                int discarded = buffer.size() + incoming;
                buffer.reset();
                log.warn("Discarding oversized audio buffer for connection {}, buffered {} bytes",
                    session.getId(), discarded);
                sendJson(session, Map.of("type", "error", "message", "音频长度超出限制，请重新开始本轮"));
                return;
            }
            buffer.write(bytes);
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

        Map<String, Object> requestMap;
        try {
            requestMap = objectMapper.readValue(message.getPayload(), new TypeReference<Map<String, Object>>() {
            });
        } catch (Exception e) {
            log.warn("WebSocket parse text payload failed: {}", message.getPayload());
            return;
        }

        String type = (String) requestMap.get("type");
        if ("start".equalsIgnoreCase(type)) {
            Number sessionIdNum = (Number) requestMap.get("sessionId");
            if (sessionIdNum != null) {
                Long requestedSessionId = sessionIdNum.longValue();
                InterviewSession interviewSession = voiceInterviewTurnService.validateActiveSession(accountId, requestedSessionId);
                if (interviewSession == null) {
                    sendJson(session, Map.of("type", "error", "message", "面试会话不可用，请刷新后重试"));
                    return;
                }
                activeSessionIds.put(session.getId(), requestedSessionId);
                ByteArrayOutputStream buffer = sessionBuffers.get(session.getId());
                if (buffer != null) {
                    buffer.reset();
                }
                log.info("Started voice session {} on connection {}", requestedSessionId, session.getId());
            }
        } else if ("stop".equalsIgnoreCase(type)) {
            Long activeSessionId = activeSessionIds.get(session.getId());
            if (activeSessionId == null) {
                sendJson(session, Map.of("type", "error", "message", "面试会话未初始化"));
                return;
            }

            ByteArrayOutputStream buffer = sessionBuffers.get(session.getId());
            if (buffer == null) {
                return;
            }
            byte[] audioBytes;
            synchronized (buffer) {
                if (buffer.size() == 0) {
                    sendJson(session, Map.of("type", "error", "message", "没有检测到任何音频数据"));
                    return;
                }
                audioBytes = buffer.toByteArray();
                buffer.reset();
            }

            voiceInterviewTurnService.processTurn(accountId, activeSessionId, audioBytes, buildSink(session));
        }
    }

    private VoiceTurnEventSink buildSink(WebSocketSession session) {
        return new VoiceTurnEventSink() {
            @Override
            public void status(String status) {
                sendJson(session, Map.of("type", "status", "status", status));
            }

            @Override
            public void userText(String text) {
                sendJson(session, Map.of("type", "user_text", "text", text));
            }

            @Override
            public void assistantText(String chunk) {
                sendJson(session, Map.of("type", "text", "chunk", chunk));
            }

            @Override
            public void audio(String base64Audio) {
                sendJson(session, Map.of("type", "audio", "data", base64Audio));
            }

            @Override
            public void judge(int score, String hint) {
                sendJson(session, Map.of("type", "judge", "score", score, "hint", hint));
            }

            @Override
            public void error(String message) {
                sendJson(session, Map.of("type", "error", "message", message));
            }

            @Override
            public Long currentActiveSessionId() {
                return activeSessionIds.get(session.getId());
            }

            @Override
            public void clearActiveSession() {
                activeSessionIds.remove(session.getId());
            }
        };
    }

    /**
     * The originating Spring Session must still be alive and owned by the same
     * account; a revoked session closes the connection with a policy status.
     */
    private boolean sessionRevoked(WebSocketSession session) {
        Long accountId = (Long) session.getAttributes().get("accountId");
        String authSessionId = (String) session.getAttributes().get("authSessionId");
        if (accountId == null || authSessionId == null) {
            return true;
        }
        return !sessionValidity.isActive(authSessionId, accountId);
    }

    private void closeRevokedSession(WebSocketSession session) throws IOException {
        sessionBuffers.remove(session.getId());
        activeSessionIds.remove(session.getId());
        outbound.remove(session.getId());
        log.info("WebSocket closed: originating session revoked, connection id: {}", session.getId());
        session.close(CloseStatus.POLICY_VIOLATION.withReason("session revoked"));
    }

    private void sendJson(WebSocketSession session, Object payload) {
        WebSocketSession target = outbound.getOrDefault(session.getId(), session);
        if (!target.isOpen()) {
            return;
        }
        try {
            String json = objectMapper.writeValueAsString(payload);
            target.sendMessage(new TextMessage(json));
        } catch (IOException e) {
            log.warn("Failed to push websocket message: {}", e.getMessage());
        }
    }
}
