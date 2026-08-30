package com.prelude.llm;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public interface LlmProvider {

    String providerKey();

    String providerName();

    String defaultModel();

    String systemApiKey();

    String chat(LlmInvocation invocation);

    void streamChat(LlmInvocation invocation, Consumer<String> onDelta);

    record LlmInvocation(
            String baseUrl,
            String model,
            String apiKey,
            List<Map<String, String>> messages,
            Integer maxTokens,
            Map<String, Object> extraParams,
            List<LlmAttachment> attachments) {

        public LlmInvocation(String baseUrl, String model, String apiKey, List<Map<String, String>> messages) {
            this(baseUrl, model, apiKey, messages, null, null, null);
        }

        public LlmInvocation {
            attachments = attachments == null ? List.of() : List.copyOf(attachments);
        }
    }
}
