package com.interview.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class OpenAiProvider extends AbstractOpenAiCompatibleProvider {

    public OpenAiProvider(
        ObjectMapper objectMapper,
        @Value("${openai.model}") String defaultModel,
        @Value("${openai.api-key:}") String systemApiKey
    ) {
        super(objectMapper, "openai", "OpenAI", defaultModel, systemApiKey);
    }
}
