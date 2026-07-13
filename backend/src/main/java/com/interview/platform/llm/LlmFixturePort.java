package com.interview.platform.llm;

public interface LlmFixturePort {

    boolean isEnabled();

    String nextStoredApiKey(String requestedApiKey, String currentEncryptedApiKey);

    String maskApiKey(String encryptedApiKey);
}
