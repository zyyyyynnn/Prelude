package com.interview.platform.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

@Component
public class OpenAiCompatibleProvider extends AbstractOpenAiCompatibleProvider {

    public static final String PROVIDER_KEY = "openai-compatible";

    public OpenAiCompatibleProvider(ObjectMapper objectMapper, LlmMetricsTracker metricsTracker) {
        super(objectMapper, PROVIDER_KEY, "OpenAI-compatible", "", "", metricsTracker);
    }
}
