package com.interview.config;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.interview.common.UserContext;
import com.interview.entity.*;
import com.interview.llm.LlmRouter;
import com.interview.service.impl.*;
import com.interview.service.VoiceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.AbstractWebSocketHandler;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Component
@RequiredArgsConstructor
public class VoiceWebSocketHandler extends AbstractWebSocketHandler {

    private final VoiceService voiceService;
    private final LlmRouter llmRouter;
    private final ObjectMapper objectMapper;
    private final InterviewStageManager interviewStageManager;
    private final InterviewContextService interviewContextService;
    private final InterviewJudgeService interviewJudgeService;
    private final InterviewSummaryService interviewSummaryService;
    private final VoiceInterviewSessionService voiceInterviewSessionService;
    private final InterviewMessageService interviewMessageService;
    
    @Qualifier("sseTaskExecutor")
    private final Executor sseTaskExecutor;

    private static final String ROLE_USER = "user";
    private static final String ROLE_ASSISTANT = "assistant";
    private static final String STAGE_COMPLETE_TAG = "[STAGE_COMPLETE]";

    // Session states
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

        String payload = message.getPayload();
        Map<String, Object> requestMap;
        try {
            requestMap = objectMapper.readValue(payload, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            log.warn("WebSocket parse text payload failed: {}", payload);
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
                    buffer.reset(); // Reset audio accumulator for a new recording
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
            buffer.reset(); // Clear buffer for next turn

            // Run processing chain asynchronously to prevent blocking WS connection thread
            sseTaskExecutor.execute(() -> {
                UserContext.setCurrentUserId(userId);
                UserContext.setCurrentSessionId(activeSessionId);
                try {
                    if (!Objects.equals(activeSessionIds.get(session.getId()), activeSessionId)) {
                        sendJson(session, Map.of("type", "error", "message", "面试会话已切换，请重新开始语音输入"));
                        return;
                    }
                    InterviewSession interviewSession = voiceInterviewSessionService.validateActiveSession(userId, activeSessionId);
                    if (interviewSession == null) {
                        activeSessionIds.remove(session.getId());
                        sendJson(session, Map.of("type", "error", "message", "面试会话不可用，请刷新后重试"));
                        return;
                    }

                    // 1. Tell client that we are transcribing speech to text
                    sendJson(session, Map.of("type", "status", "status", "stt_processing"));

                    String transcribed = voiceService.speechToText(activeSessionId, audioBytes, "voice.webm");
                    if (transcribed == null || transcribed.trim().isEmpty()) {
                        sendJson(session, Map.of("type", "error", "message", "网络状况不佳，已为您切回文字模式"));
                        return;
                    }

                    // 2. Persist user message to database
                    InterviewMessage userMsg = interviewMessageService.insertMessage(activeSessionId, ROLE_USER, transcribed);

                    // Send recognized text to display immediately in user chat bubble
                    sendJson(session, Map.of("type", "user_text", "text", transcribed));

                    // 3. Start streaming and synthesizing responses
                    sendJson(session, Map.of("type", "status", "status", "tts_processing"));

                    List<Map<String, String>> contextMessages = interviewContextService.buildContextMessages(activeSessionId);

                    StringBuilder assistantReply = new StringBuilder();
                    StringBuilder sentenceBuilder = new StringBuilder();

                    // Executor queue to keep TTS chunks sequentially played in order
                    ExecutorService ttsExecutor = Executors.newSingleThreadExecutor();
                    AtomicBoolean ttsFailed = new AtomicBoolean(false);

                    llmRouter.streamWithSnapshot(
                        interviewSession.getLlmProvider(),
                        interviewSession.getLlmModel(),
                        contextMessages,
                        delta -> {
                            // a. Stream text chunk to frontend for live subtitle rendering
                            sendJson(session, Map.of("type", "text", "chunk", delta));

                            assistantReply.append(delta);
                            sentenceBuilder.append(delta);

                            // b. Check sentence boundary
                            String sentence = extractSentenceIfComplete(sentenceBuilder);
                            if (sentence != null && !sentence.trim().isEmpty() && !ttsFailed.get()) {
                                ttsExecutor.submit(() -> {
                                    if (ttsFailed.get()) return;
                                    try {
                                        byte[] speechBytes = voiceService.textToSpeech(sentence);
                                        if (speechBytes != null && speechBytes.length > 0) {
                                            String base64 = Base64.getEncoder().encodeToString(speechBytes);
                                            sendJson(session, Map.of("type", "audio", "data", base64));
                                        }
                                    } catch (Exception e) {
                                        log.error("TTS generation failed, fallback to text-only: {}", e.getMessage());
                                        ttsFailed.set(true);
                                        sendJson(session, Map.of("type", "error", "message", "网络状况不佳，已为您切回文字模式"));
                                    }
                                });
                            }
                        }
                    );

                    // Handle remaining sentence piece after LLM stream completes
                    String remaining = sentenceBuilder.toString();
                    if (!remaining.trim().isEmpty() && !ttsFailed.get()) {
                        ttsExecutor.submit(() -> {
                            if (ttsFailed.get()) return;
                            try {
                                byte[] speechBytes = voiceService.textToSpeech(remaining);
                                if (speechBytes != null && speechBytes.length > 0) {
                                    String base64 = Base64.getEncoder().encodeToString(speechBytes);
                                    sendJson(session, Map.of("type", "audio", "data", base64));
                                }
                            } catch (Exception e) {
                                log.error("TTS last generation failed: {}", e.getMessage());
                                ttsFailed.set(true);
                                sendJson(session, Map.of("type", "error", "message", "网络状况不佳，已为您切回文字模式"));
                            }
                        });
                    }

                    // Gracefully shutdown queue and await remaining tasks
                    ttsExecutor.shutdown();
                    try {
                        ttsExecutor.awaitTermination(5, TimeUnit.SECONDS);
                    } catch (InterruptedException ignored) {}

                    // 4. Save Assistant reply
                    String finalReply = assistantReply.toString();
                    boolean shouldAdvance = false;
                    if (finalReply.contains(STAGE_COMPLETE_TAG)) {
                        finalReply = finalReply.replace(STAGE_COMPLETE_TAG, "").trim();
                        shouldAdvance = true;
                    }

                    if (!finalReply.isEmpty()) {
                        interviewMessageService.insertMessage(activeSessionId, ROLE_ASSISTANT, finalReply);
                    }
                    if (shouldAdvance) {
                        interviewStageManager.advanceStage(activeSessionId, false);
                    }

                    // 5. Notify frontend speech has completed
                    sendJson(session, Map.of("type", "status", "status", "speech_end"));

                    // 6. Async judge score evaluation & dynamic window summarization
                    triggerVoiceJudge(interviewSession, userMsg, session);
                    interviewSummaryService.triggerAsyncSummarizeIfNeeded(interviewSession, true);

                } catch (Exception e) {
                    log.error("Voice WebSocket processing chain crashed: ", e);
                    sendJson(session, Map.of("type", "error", "message", "网络状况不佳，已为您切回文字模式"));
                } finally {
                    UserContext.remove();
                }
            });
        }
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

    private String extractSentenceIfComplete(StringBuilder builder) {
        String text = builder.toString();
        int cutIdx = -1;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '。' || c == '？' || c == '！' || c == '；' || c == '\n' ||
                c == '.' || c == '?' || c == '!' || c == ';') {
                cutIdx = i;
            }
        }
        if (cutIdx != -1) {
            String sentence = text.substring(0, cutIdx + 1);
            builder.delete(0, cutIdx + 1);
            return sentence;
        }
        return null;
    }

    private void triggerVoiceJudge(InterviewSession session, InterviewMessage userMsg, WebSocketSession wsSession) {
        try {
            interviewJudgeService.judgeAndPersist(session, userMsg, true).ifPresent(result -> {
                sendJson(wsSession, Map.of(
                    "type", "judge",
                    "score", result.score(),
                    "hint", result.hint()
                ));
            });
        } catch (Exception exception) {
            log.warn("Failed to update and push message score/hint in voice mode", exception);
        }
    }
}
