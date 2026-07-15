package com.interview.platform.llm;

import com.interview.shared.api.BusinessException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CustomLlmEndpointUrlTest {

    @Test
    void normalizesRootsAndDerivesProtocolEndpoints() {
        assertThat(CustomLlmEndpointUrl.normalizeRoot(
            "https://example.com/v1/responses", CustomLlmProtocol.OPENAI_RESPONSES))
            .isEqualTo("https://example.com/v1");
        assertThat(CustomLlmEndpointUrl.toInvocationUrl(
            "https://example.com/v1/", CustomLlmProtocol.OPENAI_CHAT_COMPLETIONS))
            .isEqualTo("https://example.com/v1/chat/completions");
        assertThat(CustomLlmEndpointUrl.toInvocationUrl(
            "https://example.com/v1/messages", CustomLlmProtocol.ANTHROPIC_MESSAGES))
            .isEqualTo("https://example.com/v1/messages");
        assertThat(CustomLlmEndpointUrl.toModelsUrl(
            "https://example.com/v1", CustomLlmProtocol.OPENAI_RESPONSES))
            .isEqualTo("https://example.com/v1/models");
    }

    @Test
    void rejectsMismatchedOrUnsafeUrls() {
        assertThatThrownBy(() -> CustomLlmEndpointUrl.normalizeRoot(
            "https://example.com/v1/messages", CustomLlmProtocol.OPENAI_RESPONSES))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("协议不匹配");
        assertThatThrownBy(() -> CustomLlmEndpointUrl.toModelsUrl(
            "https://example.com/v1", CustomLlmProtocol.ANTHROPIC_MESSAGES))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("不支持自动检测模型");
        assertThatThrownBy(() -> CustomLlmEndpointUrl.normalizeRoot(
            "ftp://example.com/v1", CustomLlmProtocol.OPENAI_RESPONSES))
            .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> CustomLlmEndpointUrl.normalizeRoot(
            "https://user:secret@example.com/v1", CustomLlmProtocol.OPENAI_RESPONSES))
            .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> CustomLlmEndpointUrl.normalizeRoot(
            "https://example.com/v1?api_key=secret", CustomLlmProtocol.OPENAI_RESPONSES))
            .isInstanceOf(BusinessException.class);
    }
}
