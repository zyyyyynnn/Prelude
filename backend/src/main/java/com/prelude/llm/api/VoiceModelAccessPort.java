package com.prelude.llm.api;

/**
 * Voice transport access for the voice module: a provider base URL and its
 * runtime key, resolved from the account's frozen model configuration. Voice
 * is a real consumer of the execution snapshot boundary.
 */
public interface VoiceModelAccessPort {

    VoiceModelAccess resolveForAccount(Long accountId, Long snapshotId);

    record VoiceModelAccess(String baseUrl, String apiKey) {
    }
}
