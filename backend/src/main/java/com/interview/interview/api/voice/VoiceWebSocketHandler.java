package com.interview.interview.api.voice;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.interview.interview.domain.InterviewSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.AbstractWebSocketHandler;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
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

    private final ObjectMapper objectMapper;
    private final VoiceInterviewSessionService voiceInterviewSessionService;
    private final VoiceInterviewTurnService voiceInterviewTurnService;

    private final Map<String, ByteArrayOutputStream> sessionBuffers = new ConcurrentHashMap<>();
    private final Map<String, Long> activeSessionIds = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        Long userId = (Long) session.getAttributes().get("userId");
        if (userId == null) {
            log.warn("WebSocket connection rejected: userId not bound in session attributes");
            session.close(CloseStatus.BAD_DATA);
            return;
        }
        sessionBuffers.put(session.getId(), new ByteArrayOutputStream());
        log.info("WebSocket connection established for user {}, connection id: {}", userId, session.getId());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        sessionBuffers.remove(session.getId());
        activeSessionIds.remove(session.getId());
        log.info("WebSocket connection closed, connection id: {}", session.getId());
    }

    @Override
    protected void handleBinaryMessage(WebSocketSession session, BinaryMessage message) throws Exception {
        ByteArrayOutputStream buffer = sessionBuffers.get(session.getId());
        if (buffer != null) {
            byte[] bytes = message.getPayload().array();
            buffer.write(bytes);
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        Long userId = (Long) session.getAttributes().get("userId");
        if (userId == null) {
            session.close(CloseStatus.BAD_DATA);
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
                InterviewSession interviewSession = voiceInterviewSessionService.validateActiveSession(userId, requestedSessionId);
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
            if (buffer == null || buffer.size() == 0) {
                sendJson(session, Map.of("type", "error", "message", "没有检测到任何音频数据"));
                return;
            }

            byte[] audioBytes = buffer.toByteArray();
            buffer.reset();

            voiceInterviewTurnService.processTurn(userId, activeSessionId, audioBytes, buildSink(session));
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

    private void sendJson(WebSocketSession session, Object payload) {
        if (!session.isOpen()) {
            return;
        }
        try {
            String json = objectMapper.writeValueAsString(payload);
            session.sendMessage(new TextMessage(json));
        } catch (IOException e) {
            log.warn("Failed to push websocket message: {}", e.getMessage());
        }
    }
}
