package com.interview.service.impl;

import com.interview.config.DemoProperties;
import com.interview.entity.InterviewMessage;
import com.interview.mapper.InterviewMessageMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VoiceServiceImplTest {

    @Mock
    private DemoProperties demoProperties;

    @Mock
    private InterviewMessageMapper interviewMessageMapper;

    @InjectMocks
    private VoiceServiceImpl voiceService;

    @Test
    void testSpeechToTextDemoModeReturnsScriptedAnswers() {
        // Enable demo mode
        when(demoProperties.isEnabled()).thenReturn(true);

        // Round 0: expect warmup answer
        when(interviewMessageMapper.selectList(any())).thenReturn(Collections.emptyList());
        String res0 = voiceService.speechToText(1L, new byte[0], "voice.webm");
        assertThat(res0).contains("我主要做后端这一块");

        // Round 1: expect technical answer
        when(interviewMessageMapper.selectList(any())).thenReturn(List.of(new InterviewMessage()));
        String res1 = voiceService.speechToText(1L, new byte[0], "voice.webm");
        assertThat(res1).contains("一开始我也想过直接写在一个服务里");

        // Round 2: expect deep dive answer
        when(interviewMessageMapper.selectList(any())).thenReturn(List.of(new InterviewMessage(), new InterviewMessage()));
        String res2 = voiceService.speechToText(1L, new byte[0], "voice.webm");
        assertThat(res2).contains("如果继续做，我会先补评分解释");
    }

    @Test
    void testTextToSpeechDemoModeOrFallbackReturnsMockWav() {
        // Enable demo mode to force WAV mock synthesis
        when(demoProperties.isEnabled()).thenReturn(true);

        byte[] audioBytes = voiceService.textToSpeech("Synthesize this mock text");
        assertThat(audioBytes).isNotEmpty();
        
        // Assert standard WAV header (starts with RIFF chunk descriptor)
        String riffHeader = new String(audioBytes, 0, 4);
        assertThat(riffHeader).isEqualTo("RIFF");
        
        String waveHeader = new String(audioBytes, 8, 4);
        assertThat(waveHeader).isEqualTo("WAVE");
    }
}
