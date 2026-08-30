package com.prelude.llm;

public interface VoiceModelAccessPort {

    VoiceModelAccess resolveForAccount(Long accountId);

    record VoiceModelAccess(String baseUrl, String apiKey) {
    }
}
