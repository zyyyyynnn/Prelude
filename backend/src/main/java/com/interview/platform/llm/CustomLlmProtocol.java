package com.interview.platform.llm;

import com.interview.shared.api.BusinessException;

import java.util.Arrays;
import java.util.List;

public enum CustomLlmProtocol {
    OPENAI_RESPONSES("openai-responses", "/responses", true),
    OPENAI_CHAT_COMPLETIONS("openai-chat-completions", "/chat/completions", true),
    ANTHROPIC_MESSAGES("anthropic-messages", "/messages", false);

    private final String providerKey;
    private final String endpointSuffix;
    private final boolean modelDiscoverySupported;

    CustomLlmProtocol(String providerKey, String endpointSuffix, boolean modelDiscoverySupported) {
        this.providerKey = providerKey;
        this.endpointSuffix = endpointSuffix;
        this.modelDiscoverySupported = modelDiscoverySupported;
    }

    public String providerKey() {
        return providerKey;
    }

    public String endpointSuffix() {
        return endpointSuffix;
    }

    public boolean supportsModelDiscovery() {
        return modelDiscoverySupported;
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
