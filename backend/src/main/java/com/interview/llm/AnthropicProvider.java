package com.interview.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class AnthropicProvider extends AbstractOpenAiCompatibleProvider {

    public AnthropicProvider(
        ObjectMapper objectMapper,
        @Value("${anthropic.model}") String defaultModel,
        @Value("${anthropic.api-key:}") String systemApiKey
    ) {
        super(objectMapper, "anthropic", "Anthropic", defaultModel, systemApiKey);
    }
}
