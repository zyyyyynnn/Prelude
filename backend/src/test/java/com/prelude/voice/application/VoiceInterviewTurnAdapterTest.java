package com.prelude.voice.application;

import com.prelude.SessionKeyedSerialExecutor;
import com.prelude.interview.application.port.InterviewSessionGuard;
import com.prelude.interview.application.port.InterviewTurnPort;
import com.prelude.interview.application.port.JudgeOutcome;
import com.prelude.test.SessionFixtures;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class VoiceInterviewTurnAdapterTest {

    private final VoicePort voice = mock(VoicePort.class);
    private final InterviewSessionGuard guard = mock(InterviewSessionGuard.class);
    private final InterviewTurnPort turns = mock(InterviewTurnPort.class);
    private final VoiceTurnEventSink sink = mock(VoiceTurnEventSink.class);
    private final VoiceInterviewTurnService service = new VoiceInterviewTurnService(
        voice, guard, turns,
        new SessionKeyedSerialExecutor(Runnable::run),
        new SessionKeyedSerialExecutor(Runnable::run));

    @BeforeEach
    void setUp() {
        when(sink.isCurrent()).thenReturn(true);
        when(guard.isOngoing(7L, 51L)).thenReturn(true);
        when(voice.speechToText(7L, new byte[] {1}, "voice.webm")).thenReturn("回答");
        when(voice.textToSpeech(any(), any())).thenReturn(new byte[] {1});
        org.mockito.Mockito.doAnswer(invocation -> {
            com.prelude.interview.application.port.InterviewTurnSink turnSink = invocation.getArgument(1);
            turnSink.assistantDelta("请继续。");
            return SessionFixtures.turnResult(
                SessionFixtures.turnSession(51L), SessionFixtures.userTurn(12L, 51L, "回答"), "请继续。");
        }).when(turns).execute(any(), any());
    }

    @Test
    void completedJudgeKeepsTheExistingVoiceEvent() {
        when(turns.judgeAndPersist(51L, 12L)).thenReturn(JudgeOutcome.completed(
            SessionFixtures.judgeResult(8, "补充细节", "{}")));

        service.processTurn(7L, 51L, new byte[] {1}, sink);

        verify(sink).judge(8, "补充细节");
        verify(sink).turnFinished();
    }

    @Test
    void aSupersededTurnDoesNotStartProcessing() {
        when(sink.isCurrent()).thenReturn(false);

        service.processTurn(7L, 51L, new byte[] {1}, sink);

        verify(voice, never()).speechToText(any(), any(), any());
        verify(turns, never()).execute(any(), any());
        verify(sink).turnFinished();
    }

    @Test
    void anUnavailableSessionReportsTheErrorBeforeInvalidatingTheTurn() {
        when(guard.isOngoing(7L, 51L)).thenReturn(false);

        service.processTurn(7L, 51L, new byte[] {1}, sink);

        var order = org.mockito.Mockito.inOrder(sink);
        order.verify(sink).error("面试会话不可用，请刷新后重试");
        order.verify(sink).invalidate();
        verify(voice, never()).speechToText(any(), any(), any());
    }

    @Test
    void revocationDuringStreamingStopsBeforeAssistantPersistenceAndJudge() {
        java.util.concurrent.atomic.AtomicBoolean current = new java.util.concurrent.atomic.AtomicBoolean(true);
        when(sink.isCurrent()).thenAnswer(invocation -> current.get());
        org.mockito.Mockito.doAnswer(invocation -> {
            com.prelude.interview.application.port.InterviewTurnSink turnSink = invocation.getArgument(1);
            current.set(false);
            turnSink.assistantDelta("请继续。");
            return SessionFixtures.turnResult(
                SessionFixtures.turnSession(51L), SessionFixtures.userTurn(12L, 51L, "回答"), "请继续。");
        }).when(turns).execute(any(), any());

        service.processTurn(7L, 51L, new byte[] {1}, sink);

        verify(sink, never()).assistantText(any());
        verify(turns, never()).judgeAndPersist(any(), any());
        verify(turns, never()).summarizeIfNeeded(any());
        verify(sink).turnFinished();
    }

    @Test
    void revocationAfterStreamingSkipsRemainingSpeechSynthesis() {
        java.util.concurrent.atomic.AtomicBoolean current = new java.util.concurrent.atomic.AtomicBoolean(true);
        when(sink.isCurrent()).thenAnswer(invocation -> current.get());
        org.mockito.Mockito.doAnswer(invocation -> {
            com.prelude.interview.application.port.InterviewTurnSink turnSink = invocation.getArgument(1);
            turnSink.assistantDelta("请继续");
            current.set(false);
            return SessionFixtures.turnResult(
                SessionFixtures.turnSession(51L), SessionFixtures.userTurn(12L, 51L, "回答"), "请继续");
        }).when(turns).execute(any(), any());

        service.processTurn(7L, 51L, new byte[] {1}, sink);

        verify(voice, never()).textToSpeech(any(), any());
        verify(turns, never()).judgeAndPersist(any(), any());
    }

    @Test
    void busyJudgeDoesNotEmitAScore() {
        when(turns.judgeAndPersist(51L, 12L)).thenReturn(JudgeOutcome.busy());

        service.processTurn(7L, 51L, new byte[] {1}, sink);

        verify(sink, never()).judge(anyInt(), any());
    }

    @Test
    void unscoredJudgeDoesNotEmitAScore() {
        when(turns.judgeAndPersist(51L, 12L)).thenReturn(JudgeOutcome.unscored("model unavailable"));

        service.processTurn(7L, 51L, new byte[] {1}, sink);

        verify(sink, never()).judge(anyInt(), any());
    }

    @Test
    void ttsFailureKeepsTheTextFallbackAndContinuesJudging() {
        when(voice.textToSpeech(any(), any())).thenThrow(new IllegalStateException("tts unavailable"));
        when(turns.judgeAndPersist(51L, 12L)).thenReturn(JudgeOutcome.busy());

        service.processTurn(7L, 51L, new byte[] {1}, sink);

        verify(sink).assistantText("请继续。");
        verify(sink).error("网络状况不佳，已为您切回文字模式");
        verify(turns).judgeAndPersist(51L, 12L);
    }
}
