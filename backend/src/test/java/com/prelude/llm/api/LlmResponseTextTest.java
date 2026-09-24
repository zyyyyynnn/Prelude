package com.prelude.llm.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class LlmResponseTextTest {

    @Test
    void stripsAJsonFence() {
        assertThat(LlmResponseText.stripJsonFence("""
            ```json
            {"technical": 9}
            ```""")).isEqualTo("{\"technical\": 9}");
    }

    @Test
    void stripsABareFence() {
        assertThat(LlmResponseText.stripJsonFence("""
            ```
            {"technical": 9}
            ```""")).isEqualTo("{\"technical\": 9}");
    }

    @Test
    void leavesFenceLessTextTrimmed() {
        assertThat(LlmResponseText.stripJsonFence("  {\"technical\": 9}\n")).isEqualTo("{\"technical\": 9}");
    }
}
