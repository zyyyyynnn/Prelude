package com.interview.interview.api.voice;

import com.interview.interview.application.InterviewJudgeService;
import com.interview.interview.application.InterviewSummaryService;
import com.interview.shared.web.UserContext;
import com.interview.interview.domain.InterviewMessage;
import com.interview.interview.domain.InterviewSession;
import com.interview.interview.application.InterviewTurnCommand;
import com.interview.interview.application.InterviewTurnResult;
import com.interview.interview.application.InterviewTurnSink;
import com.interview.interview.application.RunInterviewTurn;
import com.interview.bootstrap.SessionKeyedSerialExecutor;
import com.interview.interview.application.port.VoicePort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Service
@RequiredArgsConstructor
public class VoiceInterviewTurnService {

    private static final long TTS_AWAIT_SECONDS = 30L;

    private final VoicePort voiceService;
    private final VoiceInterviewSessionService voiceInterviewSessionService;
    private final RunInterviewTurn runInterviewTurn;
    private final InterviewJudgeService interviewJudgeService;
    private final InterviewSummaryService interviewSummaryService;
    @Qualifier("sseTaskExecutor")
    private final Executor sseTaskExecutor;
    @Qualifier("ttsTaskExecutor")
    private final SessionKeyedSerialExecutor ttsTaskExecutor;

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
            if (voiceInterviewSessionService.validateActiveSession(userId, sessionId) == null) {
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
            sink.status("tts_processing");

            StringBuilder sentenceBuilder = new StringBuilder();
            List<CompletableFuture<Void>> ttsFutures = new ArrayList<>();
            AtomicBoolean ttsFailed = new AtomicBoolean(false);
            AtomicBoolean ttsTimedOut = new AtomicBoolean(false);

            InterviewTurnResult result = runInterviewTurn.execute(
                new InterviewTurnCommand(sessionId, userId, transcribed, false, false),
                new InterviewTurnSink() {
                    @Override
                    public void userAccepted(InterviewMessage userMessage) {
                        sink.userText(userMessage.getContent());
                    }

                    @Override
                    public void assistantDelta(String delta) {
                        sink.assistantText(delta);
                        sentenceBuilder.append(delta);
                        String sentence = extractSentenceIfComplete(sentenceBuilder);
                        if (sentence != null && !sentence.trim().isEmpty() && !ttsFailed.get()) {
                            ttsFutures.add(submitTtsTask(
                                sessionId,
                                () -> synthesizeSentence(sentence, sink, ttsFailed, ttsTimedOut)
                            ));
                        }
                    }
                }
            );

            String remaining = sentenceBuilder.toString();
            if (!remaining.trim().isEmpty() && !ttsFailed.get()) {
                ttsFutures.add(submitTtsTask(
                    sessionId,
                    () -> synthesizeSentence(remaining, sink, ttsFailed, ttsTimedOut)
                ));
            }
            awaitTtsFutures(ttsFutures, TTS_AWAIT_SECONDS, sessionId, sink, ttsFailed, ttsTimedOut);
            sink.status("speech_end");

            triggerJudge(result.session(), result.userMessage(), sink);
            interviewSummaryService.triggerAsyncSummarizeIfNeeded(result.session(), true);
        } catch (RuntimeException error) {
            log.error("Voice turn processing chain crashed", error);
            sink.error("网络状况不佳，已为您切回文字模式");
        } finally {
            UserContext.remove();
        }
    }

    private CompletableFuture<Void> submitTtsTask(Long sessionId, Runnable task) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        ttsTaskExecutor.executeForSession(sessionId, () -> {
            try {
                task.run();
                future.complete(null);
            } catch (RuntimeException error) {
                future.completeExceptionally(error);
            }
        });
        return future;
    }

    private void synthesizeSentence(
        String sentence,
        VoiceTurnEventSink sink,
        AtomicBoolean ttsFailed,
        AtomicBoolean ttsTimedOut
    ) {
        if (ttsFailed.get() || ttsTimedOut.get()) {
            return;
        }
        try {
            byte[] speechBytes = voiceService.textToSpeech(sentence);
            if (speechBytes != null && speechBytes.length > 0 && !ttsFailed.get() && !ttsTimedOut.get()) {
                sink.audio(Base64.getEncoder().encodeToString(speechBytes));
            }
        } catch (RuntimeException error) {
            log.warn("TTS generation failed, fallback to text-only: {}", error.getMessage());
            log.debug("TTS generation failure detail", error);
            ttsFailed.set(true);
            sink.error("网络状况不佳，已为您切回文字模式");
        }
    }

    void awaitTtsFutures(
        List<CompletableFuture<Void>> futures,
        long timeoutSeconds,
        Long sessionId,
        VoiceTurnEventSink sink,
        AtomicBoolean ttsFailed,
        AtomicBoolean ttsTimedOut
    ) {
        if (futures.isEmpty()) {
            return;
        }
        try {
            CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new))
                .get(timeoutSeconds, TimeUnit.SECONDS);
        } catch (TimeoutException timeout) {
            ttsTimedOut.set(true);
            ttsFailed.set(true);
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
            log.debug("TTS synthesis raised during turn for session {}: {}", sessionId, executionFailure.getMessage());
        }
    }

    private void triggerJudge(InterviewSession session, InterviewMessage userMessage, VoiceTurnEventSink sink) {
        if (userMessage == null) {
            return;
        }
        try {
            interviewJudgeService.judgeAndPersist(session, userMessage, true).ifPresent(result ->
                sink.judge(result.score(), result.hint())
            );
        } catch (RuntimeException error) {
            log.warn("Failed to update and push message score/hint in voice mode", error);
        }
    }

    private String extractSentenceIfComplete(StringBuilder builder) {
        String text = builder.toString();
        int cutIndex = -1;
        for (int index = 0; index < text.length(); index++) {
            char character = text.charAt(index);
            if (character == '。' || character == '？' || character == '！' || character == '；' || character == '\n'
                || character == '.' || character == '?' || character == '!' || character == ';') {
                cutIndex = index;
            }
        }
        if (cutIndex == -1) {
            return null;
        }
        String sentence = text.substring(0, cutIndex + 1);
        builder.delete(0, cutIndex + 1);
        return sentence;
    }
}
