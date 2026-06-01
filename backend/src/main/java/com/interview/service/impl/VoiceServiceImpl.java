package com.interview.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.interview.config.DemoProperties;
import com.interview.entity.InterviewMessage;
import com.interview.entity.LlmProviderConfig;
import com.interview.entity.User;
import com.interview.llm.LlmProvider;
import com.interview.mapper.InterviewMessageMapper;
import com.interview.mapper.LlmProviderConfigMapper;
import com.interview.mapper.UserMapper;
import com.interview.security.AesGcmEncryptor;
import com.interview.service.VoiceService;
import com.interview.common.UserContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class VoiceServiceImpl implements VoiceService {

    private final DemoProperties demoProperties;
    private final UserMapper userMapper;
    private final LlmProviderConfigMapper llmProviderConfigMapper;
    private final AesGcmEncryptor aesGcmEncryptor;
    private final List<LlmProvider> providers;
    private final InterviewMessageMapper interviewMessageMapper;
    private final ObjectMapper objectMapper;

    // Hard constraint: read/write/connect timeouts strictly set to 3 seconds for voice API circuit breaker
    private final OkHttpClient voiceHttpClient = new OkHttpClient.Builder()
            .connectTimeout(3, TimeUnit.SECONDS)
            .readTimeout(3, TimeUnit.SECONDS)
            .writeTimeout(3, TimeUnit.SECONDS)
            .build();

    @Override
    public String speechToText(Long sessionId, byte[] audioBytes, String filename) {
        if (demoProperties.isEnabled()) {
            return getMockScriptedAnswer(sessionId);
        }

        Map<String, String> outUrl = new HashMap<>();
        try {
            String apiKey = resolveApiKeyAndUrl(outUrl);
            String baseUrl = outUrl.get("url");
            String url = baseUrl + "/audio/transcriptions";

            RequestBody fileBody = RequestBody.create(audioBytes, MediaType.parse("audio/webm"));
            RequestBody requestBody = new MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("file", filename, fileBody)
                    .addFormDataPart("model", "whisper-1")
                    .build();

            Request request = new Request.Builder()
                    .url(url)
                    .addHeader("Authorization", "Bearer " + apiKey)
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
            log.warn("STT Whisper API failed or timed out, falling back to mock script: {}", e.getMessage());
            return getMockScriptedAnswer(sessionId);
        }
    }

    @Override
    public byte[] textToSpeech(String text) {
        if (demoProperties.isEnabled()) {
            return generateMockSpeechWav();
        }

        Map<String, String> outUrl = new HashMap<>();
        try {
            String apiKey = resolveApiKeyAndUrl(outUrl);
            String baseUrl = outUrl.get("url");
            String url = baseUrl + "/audio/speech";

            Map<String, Object> payload = new HashMap<>();
            payload.put("model", "tts-1");
            payload.put("input", text);
            payload.put("voice", "nova");

            Request request = new Request.Builder()
                    .url(url)
                    .addHeader("Authorization", "Bearer " + apiKey)
                    .post(RequestBody.create(objectMapper.writeValueAsString(payload), MediaType.parse("application/json")))
                    .build();

            try (Response response = voiceHttpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    throw new IOException("OpenAI TTS API returned non-success code: " + response.code());
                }
                return response.body() != null ? response.body().bytes() : new byte[0];
            }
        } catch (Exception e) {
            log.warn("TTS API failed or timed out, falling back to mock WAV: {}", e.getMessage());
            return generateMockSpeechWav();
        }
    }

    @Override
    public boolean isVoiceSupported() {
        return true;
    }

    /**
     * Helper to retrieve scripted answers for mock demo mode or fallback scenarios.
     */
    private String getMockScriptedAnswer(Long sessionId) {
        if (sessionId == null) {
            return "我主要做后端开发，对于高并发、高性能缓存以及分布式锁有较多的项目落地经验。";
        }
        try {
            List<InterviewMessage> list = interviewMessageMapper.selectList(new LambdaQueryWrapper<InterviewMessage>()
                    .eq(InterviewMessage::getSessionId, sessionId)
                    .eq(InterviewMessage::getRole, "user"));
            int round = list.size();
            switch (round) {
                case 0:
                    return "我主要做后端这一块，从登录鉴权、简历上传，到面试会话、SSE 流式回复和报告落库都参与了。实际开发里我花时间最多的是把阶段推进和消息记录串成闭环，保证后面回放和看板都有数据可用。";
                case 1:
                    return "一开始我也想过直接写在一个服务里，但很快发现会越来越乱。后来拆成简历解析、会话记录和报告生成三块，是为了让每块职责清楚一点。比如 Demo 模式可以复用会话和报告流程，只把模型调用替换成脚本数据。";
                case 2:
                    return "如果继续做，我会先补评分解释。现在报告能给分，但用户更关心为什么扣分、下一次该怎么改。把每个分数和具体回答片段关联起来，会比单纯多做几个页面更有价值。";
                default:
                    return "下一步我会先补可复现实验记录，包括数据版本、参数、指标和失败样本。这样后续调参或替换模型时，才能判断到底是模型改进还是数据偶然波动。";
            }
        } catch (Exception e) {
            return "我非常赞同这一观点，特别是在工程实现上保持高并发和高可用是非常核心的价值点。";
        }
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
            log.error("Failed to generate mock wav speech", exception);
            return new byte[0];
        }
    }

    private String resolveApiKeyAndUrl(Map<String, String> outUrl) {
        Long userId = UserContext.getCurrentUserId();
        if (userId == null) {
            throw new RuntimeException("用户未登录");
        }
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }

        String providerKey = user.getLlmProvider();
        if (providerKey == null || providerKey.isBlank()) {
            providerKey = "openai";
        }

        LlmProviderConfig providerConfig = llmProviderConfigMapper.selectOne(new LambdaQueryWrapper<LlmProviderConfig>()
                .eq(LlmProviderConfig::getProviderKey, providerKey)
                .eq(LlmProviderConfig::getEnabled, 1)
                .last("LIMIT 1"));

        String baseUrl = "https://api.openai.com/v1";
        if (providerConfig != null && providerConfig.getBaseUrl() != null && !providerConfig.getBaseUrl().isBlank()) {
            baseUrl = providerConfig.getBaseUrl();
        }
        outUrl.put("url", baseUrl);

        String apiKey = null;
        if (user.getLlmApiKeyEncrypted() != null && !user.getLlmApiKeyEncrypted().isBlank()) {
            try {
                apiKey = aesGcmEncryptor.decrypt(user.getLlmApiKeyEncrypted());
            } catch (Exception e) {
                log.warn("Failed to decrypt user key, fallback to system configurations");
            }
        }

        if (apiKey == null || apiKey.isBlank()) {
            String finalKey = providerKey;
            LlmProvider provider = providers.stream()
                    .filter(p -> p.providerKey().equalsIgnoreCase(finalKey))
                    .findFirst()
                    .orElse(null);
            if (provider != null) {
                apiKey = provider.systemApiKey();
            }
        }

        if (apiKey == null || apiKey.isBlank() || apiKey.startsWith("${")) {
            throw new RuntimeException("缺少可用的 OpenAI / Whisper 访问密钥");
        }
        return apiKey;
    }
}
