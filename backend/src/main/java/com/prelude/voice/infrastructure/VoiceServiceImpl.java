package com.prelude.voice.infrastructure;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.prelude.interview.application.port.VoicePort;
import com.prelude.llm.VoiceModelAccessPort;
import com.prelude.llm.VoiceModelAccessPort.VoiceModelAccess;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class VoiceServiceImpl implements VoicePort {

    @Value("${app.dev-fixtures.enabled:false}")
    private boolean devFixtureEnabled;
    private final VoiceModelAccessPort voiceModelAccessPort;
    private final ObjectMapper objectMapper;

    // Hard constraint: read/write/connect timeouts strictly set to 3 seconds for voice API circuit breaker
    private final OkHttpClient voiceHttpClient = new OkHttpClient.Builder()
            .connectTimeout(3, TimeUnit.SECONDS)
            .readTimeout(3, TimeUnit.SECONDS)
            .writeTimeout(3, TimeUnit.SECONDS)
            .build();

    @Override
    public String speechToText(Long sessionId, byte[] audioBytes, String filename) {
        if (devFixtureEnabled) {
            return getMockScriptedAnswer();
        }

        try {
            VoiceModelAccess access = voiceModelAccessPort.resolveCurrentUser();
            String url = access.baseUrl() + "/audio/transcriptions";

            RequestBody fileBody = RequestBody.create(audioBytes, MediaType.parse("audio/webm"));
            RequestBody requestBody = new MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("file", filename, fileBody)
                    .addFormDataPart("model", "whisper-1")
                    .build();

            Request request = new Request.Builder()
                    .url(url)
                    .addHeader("Authorization", "Bearer " + access.apiKey())
                    .post(requestBody)
                    .build();

            try (Response response = voiceHttpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    throw new IOException("Whisper API returned non-success code: " + response.code());
                }
                String body = response.body() != null ? response.body().string() : "{}";
                JsonNode node = objectMapper.readTree(body);
                return node.path("text").asText();
            }
        } catch (Exception e) {
            throw new IllegalStateException("语音识别服务调用失败", e);
        }
    }

    @Override
    public byte[] textToSpeech(String text) {
        if (devFixtureEnabled) {
            return generateMockSpeechWav();
        }

        try {
            VoiceModelAccess access = voiceModelAccessPort.resolveCurrentUser();
            String url = access.baseUrl() + "/audio/speech";

            Map<String, Object> payload = new HashMap<>();
            payload.put("model", "tts-1");
            payload.put("input", text);
            payload.put("voice", "nova");

            Request request = new Request.Builder()
                    .url(url)
                    .addHeader("Authorization", "Bearer " + access.apiKey())
                    .post(RequestBody.create(objectMapper.writeValueAsString(payload), MediaType.parse("application/json")))
                    .build();

            try (Response response = voiceHttpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    throw new IOException("OpenAI TTS API returned non-success code: " + response.code());
                }
                if (response.body() == null) {
                    throw new IOException("OpenAI TTS API returned an empty body");
                }
                return response.body().bytes();
            }
        } catch (Exception e) {
            throw new IllegalStateException("语音合成服务调用失败", e);
        }
    }

    @Override
    public boolean isVoiceSupported() {
        return true;
    }

    private String getMockScriptedAnswer() {
        return "我主要做后端开发，对高并发、缓存和分布式系统有较多项目经验。";
    }

    /**
     * Synthesizes 2 seconds of 440Hz Sine Wave mono audio (standard WAV format)
     * as a reliable, zero-dependency offline fallback.
     */
    private byte[] generateMockSpeechWav() {
        try {
            int sampleRate = 8000;
            int seconds = 2;
            int numSamples = sampleRate * seconds;
            byte[] data = new byte[numSamples];
            for (int i = 0; i < numSamples; i++) {
                double angle = 2.0 * Math.PI * i * 440.0 / sampleRate;
                data[i] = (byte) (Math.sin(angle) * 127 + 128);
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            DataOutputStream dos = new DataOutputStream(out);

            dos.writeBytes("RIFF");
            dos.writeInt(36 + numSamples);
            dos.writeBytes("WAVE");
            dos.writeBytes("fmt ");
            dos.writeInt(16);
            dos.writeShort(1); // PCM format
            dos.writeShort(1); // Mono channel
            dos.writeInt(sampleRate);
            dos.writeInt(sampleRate); // ByteRate: sampleRate * channels * 1 byte
            dos.writeShort(1); // BlockAlign
            dos.writeShort(8); // 8-bit sample
            dos.writeBytes("data");
            dos.writeInt(numSamples);
            dos.write(data);

            return out.toByteArray();
        } catch (Exception exception) {
            throw new IllegalStateException("开发模式语音数据生成失败", exception);
        }
    }

}
