package com.prelude.llm;

import com.prelude.test.ExceptionFixtures;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EndpointRootsTest {

    @Test
    void stripsMatchingProtocolSuffix() {
        assertThat(EndpointRoots.normalize("https://api.example.com/responses", CustomLlmProtocol.OPENAI_RESPONSES.providerKey()))
            .isEqualTo("https://api.example.com");
        assertThat(EndpointRoots.normalize("https://api.example.com/chat/completions", CustomLlmProtocol.OPENAI_CHAT_COMPLETIONS.providerKey()))
            .isEqualTo("https://api.example.com");
        assertThat(EndpointRoots.normalize("https://api.example.com/v1/messages", CustomLlmProtocol.ANTHROPIC_MESSAGES.providerKey()))
            .isEqualTo("https://api.example.com");
    }

    @Test
    void keepsNonMatchingPathAndTrimsTrailingSlash() {
        assertThat(EndpointRoots.normalize("https://api.example.com/v1/", CustomLlmProtocol.OPENAI_RESPONSES.providerKey()))
            .isEqualTo("https://api.example.com/v1");
        assertThat(EndpointRoots.normalize("https://gateway.corp:8443/llm", CustomLlmProtocol.OPENAI_RESPONSES.providerKey()))
            .isEqualTo("https://gateway.corp:8443/llm");
    }

    @Test
    void rejectsMalformedRoots() {
        ExceptionFixtures.assertBusinessException(() -> EndpointRoots.normalize("not a url", CustomLlmProtocol.OPENAI_RESPONSES.providerKey()));
    }

    @Test
    void trimsTrailingSlashWithoutTouchingOtherValues() {
        assertThat(EndpointRoots.trimTrailingSlash("https://api.openai.com/v1/"))
            .isEqualTo("https://api.openai.com/v1");
        assertThat(EndpointRoots.trimTrailingSlash("https://api.openai.com/v1"))
            .isEqualTo("https://api.openai.com/v1");
        assertThat(EndpointRoots.trimTrailingSlash(null)).isNull();
        assertThat(EndpointRoots.trimTrailingSlash("")).isEmpty();
    }
}
