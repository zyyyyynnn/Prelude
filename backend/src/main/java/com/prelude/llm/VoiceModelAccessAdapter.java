package com.prelude.llm;

import com.prelude.BusinessException;
import com.prelude.llm.api.VoiceModelAccessPort;
import com.prelude.llm.persistence.ModelExecutionSnapshot;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Resolves voice transport credentials. A run with a custom OpenAI-compatible
 * endpoint uses its frozen base URL and account key; built-in OpenAI uses the
 * account key or the deployment key. The credential never crosses boundaries.
 */
@Service
@RequiredArgsConstructor
public class VoiceModelAccessAdapter implements VoiceModelAccessPort {

    private final ModelProfileService profileService;
    private final ModelExecutionSnapshotService snapshotService;

    @Value("${prelude.llm.provider.openai.base-url:https://api.openai.com/v1}")
    private String openAiBaseUrl;

    @Value("${prelude.llm.provider.openai.api-key:}")
    private String openAiSystemKey;

    @Override
    public VoiceModelAccess resolveForAccount(Long accountId, Long snapshotId) {
        String baseUrl = openAiBaseUrl;
        String apiKey = null;
        if (snapshotId != null) {
            ModelExecutionSnapshot snapshot = snapshotService.require(snapshotId);
            if (ModelCapabilityCatalog.PROVIDER_OPENAI_COMPATIBLE.equals(snapshot.getProvider())) {
                baseUrl = snapshot.getCustomEndpointUrl();
                apiKey = profileService.resolveApiKey(accountId, snapshot.getCredentialId());
            } else if (ModelCapabilityCatalog.PROVIDER_OPENAI.equals(snapshot.getProvider())) {
                apiKey = profileService.resolveApiKey(accountId, snapshot.getCredentialId());
            }
        }
        if (apiKey == null || apiKey.isBlank()) {
            if (openAiSystemKey == null || openAiSystemKey.isBlank() || openAiSystemKey.startsWith("${")) {
                throw new IllegalStateException("缺少可用的 OpenAI 语音访问密钥");
            }
            apiKey = openAiSystemKey;
        }
        return new VoiceModelAccess(normalizeRoot(baseUrl), apiKey);
    }

    private String normalizeRoot(String baseUrl) {
        String trimmed = baseUrl == null ? "" : baseUrl.trim();
        return trimmed.endsWith("/") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
    }
}
