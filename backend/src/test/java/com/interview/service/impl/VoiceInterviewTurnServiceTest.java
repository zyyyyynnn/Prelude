package com.interview.service.impl;

import com.interview.entity.InterviewMessage;
import com.interview.entity.InterviewSession;
import com.interview.llm.LlmRouter;
import com.interview.mapper.InterviewMessageMapper;
import com.interview.mapper.InterviewSessionMapper;
import com.interview.service.VoiceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VoiceInterviewTurnServiceTest {

    @Mock
    private VoiceService voiceService;
    @Mock
    private LlmRouter llmRouter;
    @Mock
    private InterviewSessionMapper interviewSessionMapper;
    @Mock
    private InterviewMessageMapper interviewMessageMapper;
    @Mock
    private InterviewStageManager interviewStageManager;
    @Mock
    private InterviewContextService interviewContextService;
    @Mock
    private InterviewJudgeService interviewJudgeService;
    @Mock
    private InterviewSummaryService interviewSummaryService;
    @Mock
    private VoiceTurnEventSink sink;

    private VoiceInterviewTurnService turnService;
    private VoiceInterviewSessionService voiceInterviewSessionService;

    @BeforeEach
    void setUp() {
        Executor directExecutor = Runnable::run;
        voiceInterviewSessionService = new VoiceInterviewSessionService(interviewSessionMapper);
        InterviewMessageService interviewMessageService = new InterviewMessageService(interviewMessageMapper);
        turnService = new VoiceInterviewTurnService(
            voiceService,
            llmRouter,
            interviewStageManager,
            interviewContextService,
            interviewJudgeService,
            interviewSummaryService,
            voiceInterviewSessionService,
            interviewMessageService,
            directExecutor,
            directExecutor
        );
    }

    @Test
    void sessionSwitchedMidTurnReportsSwitchErrorAndSkipsProcessing() {
        when(sink.currentActiveSessionId()).thenReturn(99L);

        turnService.processTurn(42L, 7L, new byte[] {1, 2, 3}, sink);

        verify(sink).error("面试会话已切换，请重新开始语音输入");
        verify(sink, never()).status(anyString());
        verifyNoVoiceProcessing();
    }

    @Test
    void sessionRevalidationFailureClearsActiveSessionAndReportsError() {
        when(sink.currentActiveSessionId()).thenReturn(7L);
        when(interviewSessionMapper.selectById(7L)).thenReturn(null);

        turnService.processTurn(42L, 7L, new byte[] {1, 2, 3}, sink);

        verify(sink).clearActiveSession();
        verify(sink).error("面试会话不可用，请刷新后重试");
        verifyNoVoiceProcessing();
    }

    @Test
    void emptySpeechToTextReportsFallbackError() {
        InterviewSession ongoing = session(7L, 42L, "ongoing");
        when(sink.currentActiveSessionId()).thenReturn(7L);
        when(interviewSessionMapper.selectById(7L)).thenReturn(ongoing);
        when(voiceService.speechToText(eq(7L), any(byte[].class), eq("voice.webm"))).thenReturn("");

        turnService.processTurn(42L, 7L, new byte[] {1, 2, 3}, sink);

        verify(sink).status("stt_processing");
        verify(sink).error("网络状况不佳，已为您切回文字模式");
        verify(sink, never()).userText(anyString());
    }

    @Test
    void successfulTurnPersistsUserMessageWithNextSeqNumAndEmitsUserText() {
        InterviewSession ongoing = session(7L, 42L, "ongoing");
        InterviewMessage latest = new InterviewMessage();
        latest.setSeqNum(5);
        when(sink.currentActiveSessionId()).thenReturn(7L);
        when(interviewSessionMapper.selectById(7L)).thenReturn(ongoing);
        when(voiceService.speechToText(eq(7L), any(byte[].class), eq("voice.webm"))).thenReturn("语音回答");
        when(interviewMessageMapper.selectOne(any())).thenReturn(latest);
        when(interviewContextService.buildContextMessages(7L)).thenReturn(List.of());
        org.mockito.Mockito.doAnswer(inv -> {
            return null;
        }).when(llmRouter).streamWithSnapshot(anyString(), anyString(), any(), any());

        turnService.processTurn(42L, 7L, new byte[] {1, 2, 3}, sink);

        ArgumentCaptor<InterviewMessage> messageCaptor = ArgumentCaptor.forClass(InterviewMessage.class);
        verify(interviewMessageMapper).insert(messageCaptor.capture());
        assertThat(messageCaptor.getValue().getRole()).isEqualTo("user");
        assertThat(messageCaptor.getValue().getSeqNum()).isEqualTo(6);
        verify(sink).userText("语音回答");
    }

    @Test
    void stageCompleteTagTriggersStageAdvance() {
        InterviewSession ongoing = session(7L, 42L, "ongoing");
        when(sink.currentActiveSessionId()).thenReturn(7L);
        when(interviewSessionMapper.selectById(7L)).thenReturn(ongoing);
        when(voiceService.speechToText(eq(7L), any(byte[].class), eq("voice.webm"))).thenReturn("ok");
        when(interviewContextService.buildContextMessages(7L)).thenReturn(List.of());
        org.mockito.Mockito.doAnswer(inv -> {
            Consumer<String> onDelta = inv.getArgument(3);
            onDelta.accept("好的，我来结束[STAGE_COMPLETE]");
            return null;
        }).when(llmRouter).streamWithSnapshot(anyString(), anyString(), any(), any());

        turnService.processTurn(42L, 7L, new byte[] {1, 2, 3}, sink);

        verify(interviewStageManager).advanceStage(7L, false);
    }

    @Test
    void judgeAndSummaryAreTriggeredAtTurnEnd() {
        InterviewSession ongoing = session(7L, 42L, "ongoing");
        when(sink.currentActiveSessionId()).thenReturn(7L);
        when(interviewSessionMapper.selectById(7L)).thenReturn(ongoing);
        when(voiceService.speechToText(eq(7L), any(byte[].class), eq("voice.webm"))).thenReturn("ok");
        when(interviewContextService.buildContextMessages(7L)).thenReturn(List.of());
        org.mockito.Mockito.doAnswer(inv -> {
            Consumer<String> onDelta = inv.getArgument(3);
            onDelta.accept("回复。");
            return null;
        }).when(llmRouter).streamWithSnapshot(anyString(), anyString(), any(), any());
        when(interviewJudgeService.judgeAndPersist(any(), any(), eq(true)))
            .thenReturn(Optional.of(new InterviewJudgeService.JudgeResult(8, "hint", "{}")));

        turnService.processTurn(42L, 7L, new byte[] {1, 2, 3}, sink);

        verify(sink).status("speech_end");
        verify(sink).judge(8, "hint");
        verify(interviewSummaryService).triggerAsyncSummarizeIfNeeded(ongoing, true);
    }

    @Test
    void ttsFailureFallsBackToErrorAndSkipsRemainingSynthesis() {
        InterviewSession ongoing = session(7L, 42L, "ongoing");
        when(sink.currentActiveSessionId()).thenReturn(7L);
        when(interviewSessionMapper.selectById(7L)).thenReturn(ongoing);
        when(voiceService.speechToText(eq(7L), any(byte[].class), eq("voice.webm"))).thenReturn("ok");
        when(interviewContextService.buildContextMessages(7L)).thenReturn(List.of());
        org.mockito.Mockito.doAnswer(inv -> {
            Consumer<String> onDelta = inv.getArgument(3);
            onDelta.accept("第一句。第二句。");
            return null;
        }).when(llmRouter).streamWithSnapshot(anyString(), anyString(), any(), any());
        when(voiceService.textToSpeech(anyString())).thenThrow(new RuntimeException("tts down"));

        turnService.processTurn(42L, 7L, new byte[] {1, 2, 3}, sink);

        verify(voiceService, times(1)).textToSpeech(anyString());
        verify(sink, times(1)).error("网络状况不佳，已为您切回文字模式");
        verify(sink, never()).audio(anyString());
    }

    @Test
    void multipleSentencesInvokeTextToSpeechInSubmissionOrderAndPushAudio() {
        InterviewSession ongoing = session(7L, 42L, "ongoing");
        when(sink.currentActiveSessionId()).thenReturn(7L);
        when(interviewSessionMapper.selectById(7L)).thenReturn(ongoing);
        when(voiceService.speechToText(eq(7L), any(byte[].class), eq("voice.webm"))).thenReturn("ok");
        when(interviewContextService.buildContextMessages(7L)).thenReturn(List.of());
        // "第一句。" is closed by the punctuation; "第二句" stays in the sentenceBuilder and is
        // submitted as the trailing remainder. Expect two synthesizeSentence calls in submission order.
        org.mockito.Mockito.doAnswer(inv -> {
            Consumer<String> onDelta = inv.getArgument(3);
            onDelta.accept("第一句。第二句");
            return null;
        }).when(llmRouter).streamWithSnapshot(anyString(), anyString(), any(), any());
        org.mockito.Mockito.doAnswer(inv -> {
            String arg = inv.getArgument(0);
            return arg.equals("第一句。") ? new byte[] {1} : new byte[] {2};
        }).when(voiceService).textToSpeech(anyString());

        turnService.processTurn(42L, 7L, new byte[] {1, 2, 3}, sink);

        // Two distinct TTS tasks were submitted, in order.
        ArgumentCaptor<String> sentenceCaptor = ArgumentCaptor.forClass(String.class);
        verify(voiceService, times(2)).textToSpeech(sentenceCaptor.capture());
        assertThat(sentenceCaptor.getAllValues()).containsExactly("第一句。", "第二句");
        // Two audio pushes, in submission order, base64 of the byte[] we returned.
        ArgumentCaptor<String> audioCaptor = ArgumentCaptor.forClass(String.class);
        verify(sink, times(2)).audio(audioCaptor.capture());
        assertThat(audioCaptor.getAllValues())
            .containsExactly(
                java.util.Base64.getEncoder().encodeToString(new byte[] {1}),
                java.util.Base64.getEncoder().encodeToString(new byte[] {2})
            );
        verify(sink, never()).error(anyString());
    }

    @Test
    void ttsFailureClearsUserContext() {
        InterviewSession ongoing = session(7L, 42L, "ongoing");
        when(sink.currentActiveSessionId()).thenReturn(7L);
        when(interviewSessionMapper.selectById(7L)).thenReturn(ongoing);
        when(voiceService.speechToText(eq(7L), any(byte[].class), eq("voice.webm"))).thenReturn("ok");
        when(interviewContextService.buildContextMessages(7L)).thenReturn(List.of());
        org.mockito.Mockito.doAnswer(inv -> {
            Consumer<String> onDelta = inv.getArgument(3);
            onDelta.accept("一句话。");
            return null;
        }).when(llmRouter).streamWithSnapshot(anyString(), anyString(), any(), any());
        when(voiceService.textToSpeech(anyString())).thenThrow(new RuntimeException("tts down"));

        turnService.processTurn(42L, 7L, new byte[] {1, 2, 3}, sink);

        org.assertj.core.api.Assertions.assertThat(com.interview.common.UserContext.getCurrentUserId()).isNull();
        org.assertj.core.api.Assertions.assertThat(com.interview.common.UserContext.getCurrentSessionId()).isNull();
    }

    @Test
    void awaitTtsFuturesTimesOutAndTripsStopFlags() {
        // Never-completing future simulates a stuck TTS call. timeout=0 forces an immediate
        // TimeoutException without sleeping, so the test runs in milliseconds.
        List<CompletableFuture<Void>> stuck = List.of(new CompletableFuture<>());
        AtomicBoolean ttsFailed = new AtomicBoolean(false);
        AtomicBoolean ttsTimedOut = new AtomicBoolean(false);

        turnService.awaitTtsFutures(stuck, 0L, 7L, sink, ttsFailed, ttsTimedOut);

        assertThat(ttsTimedOut.get()).isTrue();
        assertThat(ttsFailed.get()).isTrue();
        verify(sink).error("网络状况不佳，已为您切回文字模式");
    }

    @Test
    void awaitTtsFuturesHandlesEmptyListWithoutInvokingSink() {
        AtomicBoolean ttsFailed = new AtomicBoolean(false);
        AtomicBoolean ttsTimedOut = new AtomicBoolean(false);

        turnService.awaitTtsFutures(List.of(), 0L, 7L, sink, ttsFailed, ttsTimedOut);

        assertThat(ttsTimedOut.get()).isFalse();
        assertThat(ttsFailed.get()).isFalse();
        verify(sink, never()).error(anyString());
    }

    private void verifyNoVoiceProcessing() {
        verify(voiceService, never()).speechToText(anyLong(), any(byte[].class), anyString());
        verify(llmRouter, never()).streamWithSnapshot(anyString(), anyString(), any(), any());
        verify(interviewJudgeService, never()).judgeAndPersist(any(), any(), anyBoolean());
        verify(interviewSummaryService, never()).triggerAsyncSummarizeIfNeeded(any(), anyBoolean());
    }

    private InterviewSession session(Long id, Long userId, String status) {
        InterviewSession session = new InterviewSession();
        session.setId(id);
        session.setUserId(userId);
        session.setStatus(status);
        session.setLlmProvider("deepseek");
        session.setLlmModel("deepseek-chat");
        return session;
    }
}
