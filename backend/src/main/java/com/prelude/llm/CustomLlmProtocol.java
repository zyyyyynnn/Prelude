package com.prelude.llm;

import com.prelude.BusinessException;

import java.util.Arrays;
import java.util.List;

/** The three user-configured HTTP protocol contracts supported by Prelude. */
public enum CustomLlmProtocol {
    OPENAI_RESPONSES("openai-responses", "OpenAI Responses", "/responses"),
    OPENAI_CHAT_COMPLETIONS("openai-chat-completions", "OpenAI Chat Completions", "/chat/completions"),
    ANTHROPIC_MESSAGES("anthropic-messages", "Anthropic Messages", "/v1/messages");

    private final String providerKey;
    private final String displayName;
    private final String endpointSuffix;

    CustomLlmProtocol(String providerKey, String displayName, String endpointSuffix) {
        this.providerKey = providerKey;
        this.displayName = displayName;
        this.endpointSuffix = endpointSuffix;
    }

    public String providerKey() {
        return providerKey;
    }

    public String displayName() {
        return displayName;
    }

    public String endpointSuffix() {
        return endpointSuffix;
    }

    public static boolean isCustom(String providerKey) {
        return Arrays.stream(values()).anyMatch(protocol -> protocol.providerKey.equals(providerKey));
    }

    public static CustomLlmProtocol require(String providerKey) {
        return Arrays.stream(values())
            .filter(protocol -> protocol.providerKey.equals(providerKey))
            .findFirst()
            .orElseThrow(() -> BusinessException.badRequest("不支持的自定义接口协议"));
    }

    public static List<String> providerKeys() {
        return Arrays.stream(values()).map(CustomLlmProtocol::providerKey).toList();
    }
}
