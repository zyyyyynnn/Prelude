package com.prelude.llm;

import org.springframework.stereotype.Component;

@Component("llmFixtureAdapter")
class FixtureAdapter implements LlmFixturePort {
    @Override public boolean isEnabled() { return false; }
    @Override public String nextStoredApiKey(String requestedApiKey, String currentEncryptedApiKey) { return currentEncryptedApiKey; }
    @Override public String maskApiKey(String encryptedApiKey) { return null; }
}
