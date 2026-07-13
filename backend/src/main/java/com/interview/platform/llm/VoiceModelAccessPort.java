package com.interview.platform.llm;

public interface VoiceModelAccessPort {

    VoiceModelAccess resolveCurrentUser();

    record VoiceModelAccess(String baseUrl, String apiKey) {
    }
}
