package com.interview.service.impl;

import com.interview.common.UserContext;
import com.interview.entity.InterviewMessage;
import com.interview.entity.InterviewSession;
import com.interview.llm.LlmRouter;
import com.interview.service.VoiceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Owns the per-turn processing chain for the voice WebSocket pipeline.
 *
 * The WebSocket handler hands off a finished audio buffer plus a
 * {@link VoiceTurnEventSink}; this service runs the heavy pipeline
 * (STT, persistence, LLM streaming, TTS, stage advance, judge, summary)
 * off the transport thread and reports progress through the sink.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VoiceInterviewTurnService {

    private static final String ROLE_USER = "user";
    private static final String ROLE_ASSISTANT = "assistant";
    private static final String STAGE_COMPLETE_TAG = "[STAGE_COMPLETE]";
    private static final long TTS_AWAIT_SECONDS = 30L;

    private final VoiceService voiceService;
    private final LlmRouter llmRouter;
    private final InterviewStageManager interviewStageManager;
    private final InterviewContextService interviewContextService;
    private final InterviewJudgeService interviewJudgeService;
    private final InterviewSummaryService interviewSummaryService;
    private final VoiceInterviewSessionService voiceInterviewSessionService;
    private final InterviewMessageService interviewMessageService;

    @Qualifier("sseTaskExecutor")
    private final Executor sseTaskExecutor;

    @Qualifier("ttsTaskExecutor")
    private final Executor ttsTaskExecutor;

    public void processTurn(Long userId, Long sessionId, byte[] audioBytes, VoiceTurnEventSink sink) {
        sseTaskExecutor.execute(() -> runTurn(userId, sessionId, audioBytes, sink));
    }

    private void runTurn(Long userId, Long sessionId, byte[] audioBytes, VoiceTurnEventSink sink) {
        UserContext.setCurrentUserId(userId);
        UserContext.setCurrentSessionId(sessionId);
        try {
            if (!Objects.equals(sink.currentActiveSessionId(), sessionId)) {
                sink.error("面试会话已切换，请重新开始语音输入");
                return;
            }
            InterviewSession interviewSession = voiceInterviewSessionService.validateActiveSession(userId, sessionId);
            if (interviewSession == null) {
                sink.clearActiveSession();
                sink.error("面试会话不可用，请刷新后重试");
                return;
            }

            sink.status("stt_processing");

            String transcribed = voiceService.speechToText(sessionId, audioBytes, "voice.webm");
            if (transcribed == null || transcribed.trim().isEmpty()) {
                sink.error("网络状况不佳，已为您切回文字模式");
                return;
            }

            InterviewMessage userMsg = interviewMessageService.insertMessage(sessionId, ROLE_USER, transcribed);
            sink.userText(transcribed);

            sink.status("tts_processing");

            List<Map<String, String>> contextMessages = interviewContextService.buildContextMessages(sessionId);

            StringBuilder assistantReply = new StringBuilder();
            StringBuilder sentenceBuilder = new StringBuilder();

            List<CompletableFuture<Void>> ttsFutures = new ArrayList<>();
            AtomicBoolean ttsFailed = new AtomicBoolean(false);
            AtomicBoolean ttsTimedOut = new AtomicBoolean(false);

            llmRouter.streamWithSnapshot(
                interviewSession.getLlmProvider(),
                interviewSession.getLlmModel(),
                contextMessages,
                delta -> {
                    sink.assistantText(delta);
                    assistantReply.append(delta);
                    sentenceBuilder.append(delta);

                    String sentence = extractSentenceIfComplete(sentenceBuilder);
                    if (sentence != null && !sentence.trim().isEmpty() && !ttsFailed.get()) {
                        ttsFutures.add(CompletableFuture.runAsync(
                            () -> synthesizeSentence(sentence, sink, ttsFailed, ttsTimedOut), ttsTaskExecutor));
                    }
                }
            );

            String remaining = sentenceBuilder.toString();
            if (!remaining.trim().isEmpty() && !ttsFailed.get()) {
                ttsFutures.add(CompletableFuture.runAsync(
                    () -> synthesizeSentence(remaining, sink, ttsFailed, ttsTimedOut), ttsTaskExecutor));
            }

            awaitTtsFutures(ttsFutures, TTS_AWAIT_SECONDS, sessionId, sink, ttsFailed, ttsTimedOut);

            String finalReply = assistantReply.toString();
            boolean shouldAdvance = false;
            if (finalReply.contains(STAGE_COMPLETE_TAG)) {
                finalReply = finalReply.replace(STAGE_COMPLETE_TAG, "").trim();
                shouldAdvance = true;
            }

            if (!finalReply.isEmpty()) {
                interviewMessageService.insertMessage(sessionId, ROLE_ASSISTANT, finalReply);
            }
            if (shouldAdvance) {
                interviewStageManager.advanceStage(sessionId, false);
            }

            sink.status("speech_end");

            triggerJudge(interviewSession, userMsg, sink);
            interviewSummaryService.triggerAsyncSummarizeIfNeeded(interviewSession, true);

        } catch (Exception e) {
            log.error("Voice turn processing chain crashed: ", e);
            sink.error("网络状况不佳，已为您切回文字模式");
        } finally {
            UserContext.remove();
        }
    }

    private void synthesizeSentence(String sentence, VoiceTurnEventSink sink, AtomicBoolean ttsFailed, AtomicBoolean ttsTimedOut) {
        if (ttsFailed.get() || ttsTimedOut.get()) {
            return;
        }
        try {
            byte[] speechBytes = voiceService.textToSpeech(sentence);
            if (speechBytes != null && speechBytes.length > 0) {
                // Re-check stop flags right before pushing audio: a later sentence may have tripped
                // ttsTimedOut/ttsFailed after this task was already queued on the single-thread executor.
                if (ttsFailed.get() || ttsTimedOut.get()) {
                    return;
                }
                String base64 = Base64.getEncoder().encodeToString(speechBytes);
                sink.audio(base64);
            }
        } catch (Exception e) {
            // TTS failures are recovered by the text-only fallback (sink.error below);
            // demoting to WARN keeps expected-failure test paths out of the ERROR
            // signal channel without losing observability. The full stack is still
            // available at DEBUG for local triage.
            log.warn("TTS generation failed, fallback to text-only: {}", e.getMessage());
            log.debug("TTS generation failure detail", e);
            ttsFailed.set(true);
            sink.error("网络状况不佳，已为您切回文字模式");
        }
    }

    /**
     * Package-private for test access only. Production code should always pass
     * {@link #TTS_AWAIT_SECONDS} as the timeout. Tests can pass {@code 0} with a never-completing
     * future to assert the timeout branch deterministically without sleeping.
     */
    void awaitTtsFutures(List<CompletableFuture<Void>> futures, long timeoutSeconds, Long sessionId,
                         VoiceTurnEventSink sink, AtomicBoolean ttsFailed, AtomicBoolean ttsTimedOut) {
        if (futures.isEmpty()) {
            return;
        }
        try {
            CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new))
                .get(timeoutSeconds, TimeUnit.SECONDS);
        } catch (TimeoutException timeout) {
            ttsTimedOut.set(true);
            ttsFailed.set(true);
            // timeoutSeconds <= 0 is the synthetic "immediate timeout" path used
            // only by awaitTtsFuturesTimesOutAndTripsStopFlags (see the Javadoc on
            // awaitTtsFutures). Demote to DEBUG so the test's expected-timeout log
            // line does not pollute the WARN channel. The real production timeout
            // (TTS_AWAIT_SECONDS = 30) still surfaces as WARN so operators see
            // genuine end-user stalls.
            if (timeoutSeconds <= 0) {
                log.debug("TTS await timed out immediately for session {}", sessionId);
            } else {
                log.warn("TTS await timed out after {}s for session {}", timeoutSeconds, sessionId);
            }
            sink.error("网络状况不佳，已为您切回文字模式");
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            ttsTimedOut.set(true);
            ttsFailed.set(true);
            sink.error("网络状况不佳，已为您切回文字模式");
        } catch (ExecutionException executionFailure) {
            // individual synthesizeSentence already surfaced the error via sink.error;
            // the AtomicBoolean stops later sentences from re-emitting.
            log.debug("TTS synthesis raised during turn for session {}: {}", sessionId, executionFailure.getMessage());
        }
    }

    private void triggerJudge(InterviewSession session, InterviewMessage userMsg, VoiceTurnEventSink sink) {
        try {
            interviewJudgeService.judgeAndPersist(session, userMsg, true).ifPresent(result ->
                sink.judge(result.score(), result.hint())
            );
        } catch (Exception exception) {
            log.warn("Failed to update and push message score/hint in voice mode", exception);
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
}
