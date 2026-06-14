package com.interview.llm;

import com.interview.common.BusinessException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OpenAiCompatibleUrlTest {

    @Test
    void normalizesRootAndChatCompletionsEndpointToRoot() {
        assertThat(OpenAiCompatibleUrl.normalizeRoot("https://example.com/v1/"))
            .isEqualTo("https://example.com/v1");
        assertThat(OpenAiCompatibleUrl.normalizeRoot("https://example.com/v1/chat/completions"))
            .isEqualTo("https://example.com/v1");
    }

    @Test
    void derivesModelsAndChatCompletionsUrlsFromRoot() {
        String root = OpenAiCompatibleUrl.normalizeRoot("https://example.com/v1/");

        assertThat(OpenAiCompatibleUrl.toModelsUrl(root))
            .isEqualTo("https://example.com/v1/models");
        assertThat(OpenAiCompatibleUrl.toChatCompletionsUrl(root))
            .isEqualTo("https://example.com/v1/chat/completions");
    }

    @Test
    void rejectsUnsafeOrAmbiguousUrls() {
        assertThatThrownBy(() -> OpenAiCompatibleUrl.normalizeRoot(""))
            .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> OpenAiCompatibleUrl.normalizeRoot("ftp://example.com/v1"))
            .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> OpenAiCompatibleUrl.normalizeRoot("https://user:secret@example.com/v1"))
            .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> OpenAiCompatibleUrl.normalizeRoot("https://example.com/v1?api_key=secret"))
            .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> OpenAiCompatibleUrl.normalizeRoot("https://example.com/v1#models"))
            .isInstanceOf(BusinessException.class);
    }
}
