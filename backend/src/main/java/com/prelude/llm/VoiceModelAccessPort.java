package com.prelude.llm;

public interface VoiceModelAccessPort {

    VoiceModelAccess resolveCurrentUser();

    record VoiceModelAccess(String baseUrl, String apiKey) {
    }
}
