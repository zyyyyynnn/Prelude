package com.prelude.llm.api;

/**
 * Voice transport access for the voice module: the deployment-owned provider
 * base URL and system key. Account chat credentials are not reused for voice.
 */
public interface VoiceModelAccessPort {

    VoiceModelAccess resolveForAccount(Long accountId, Long snapshotId);

    record VoiceModelAccess(String baseUrl, String apiKey) {
    }
}
