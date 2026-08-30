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

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class VoiceServiceImpl implements VoicePort {

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

}
