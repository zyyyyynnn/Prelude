package com.interview.resume.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.interview.platform.llm.ChatPort;
import com.interview.platform.llm.PromptRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LlmResumeParserTest {

    @Test
    void parsesFencedStructuredResponse() {
        ChatPort chatPort = mock(ChatPort.class);
        PromptRegistry promptRegistry = mock(PromptRegistry.class);
        when(promptRegistry.load(anyString(), anyString())).thenReturn("parse prompt");
        when(chatPort.complete(any())).thenReturn("""
            ```json
            {"skills":["Java"],"projects":[{"name":"Prelude","description":"面试系统"}]}
            ```
            """);

        var parsed = new LlmResumeParser(chatPort, promptRegistry, new ObjectMapper())
            .parse(42L, "raw resume");

        assertThat(parsed.skills()).containsExactly("Java");
        assertThat(parsed.projects()).singleElement().satisfies(project ->
            assertThat(project.name()).isEqualTo("Prelude")
        );
    }
}
