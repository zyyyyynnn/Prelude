package com.prelude.llm;

import com.prelude.llm.api.VoiceModelAccessPort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Resolves the deployment-owned OpenAI voice transport. Chat protocol BYOK
 * credentials are not reused for realtime voice because the three custom
 * chat protocols do not establish a realtime-voice contract.
 */
@Service
public class VoiceModelAccessAdapter implements VoiceModelAccessPort {

    @Value("${prelude.llm.provider.openai.base-url:https://api.openai.com/v1}")
    private String openAiBaseUrl;

    @Value("${prelude.llm.provider.openai.api-key:}")
    private String openAiSystemKey;

    @Override
    public VoiceModelAccess resolveForAccount(Long accountId, Long snapshotId) {
        if (openAiSystemKey == null || openAiSystemKey.isBlank() || openAiSystemKey.startsWith("${")) {
            throw new IllegalStateException("缺少可用的 OpenAI 语音访问密钥");
        }
        return new VoiceModelAccess(normalizeRoot(openAiBaseUrl), openAiSystemKey);
    }

    private String normalizeRoot(String baseUrl) {
        String trimmed = baseUrl == null ? "" : baseUrl.trim();
        return trimmed.endsWith("/") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
    }
}
