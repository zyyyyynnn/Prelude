package com.interview.platform.llm;

import com.interview.shared.api.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ClasspathPromptRegistryTest {

    private final ClasspathPromptRegistry registry = new ClasspathPromptRegistry(
        new DefaultResourceLoader()
    );

    @Test
    void loadsVersionedUtf8Prompt() {
        assertThat(registry.load(PromptVersions.RESUME_PARSE, PromptVersions.V1))
            .contains("简历解析助手")
            .contains("skills");
    }

    @Test
    void rejectsUnsafePromptPath() {
        assertThatThrownBy(() -> registry.load("../secret", PromptVersions.V1))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("不合法");
    }
}
