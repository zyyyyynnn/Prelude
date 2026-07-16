package com.interview.platform.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class OpenAiChatCompletionsProvider extends AbstractChatCompletionsProvider {

    public static final String PROVIDER_KEY = "openai-chat-completions";

    public OpenAiChatCompletionsProvider(
        ObjectMapper objectMapper,
        LlmMetricsTracker metricsTracker,
        CustomLlmHttpClient httpClient
    ) {
        super(objectMapper, PROVIDER_KEY, "OpenAI Chat Completions", "", "", metricsTracker, httpClient);
    }

    @Override
    protected void applyExtraParams(Map<String, Object> payload, Map<String, Object> extraParams) {
        extraParams.forEach((key, value) -> {
            if ("thinking_depth".equals(key)) {
                payload.put("reasoning_effort", value);
            } else {
                payload.put(key, value);
            }
        });
    }
}
