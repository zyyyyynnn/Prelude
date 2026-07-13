package com.interview.platform.llm;

public interface PromptRegistry {

    String load(String promptId, String version);
}
