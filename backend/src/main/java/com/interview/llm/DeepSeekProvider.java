package com.interview.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class DeepSeekProvider extends AbstractOpenAiCompatibleProvider {

    public DeepSeekProvider(
        ObjectMapper objectMapper,
        @Value("${deepseek.model}") String defaultModel,
        @Value("${deepseek.api-key:}") String systemApiKey
    ) {
        super(objectMapper, "deepseek", "DeepSeek", defaultModel, systemApiKey);
    }
}
