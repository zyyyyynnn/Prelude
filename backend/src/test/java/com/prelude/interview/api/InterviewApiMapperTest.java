package com.prelude.interview.api;

import com.prelude.interview.application.InterviewSessionDetails;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class InterviewApiMapperTest {

    @Test
    void exposesTheFrozenSessionModel() {
        InterviewSessionDetails details = new InterviewSessionDetails(
            42L,
            "前端工程师",
            "finished",
            "closing",
            "deepseek-v4-pro",
            "HIGH",
            "{}",
            List.of(),
            List.of(),
            3L,
            5L,
            null,
            List.of()
        );

        InterviewMessagesResponse response = InterviewApiMapper.toResponse(details);
        assertThat(response.model()).isEqualTo("deepseek-v4-pro");
        assertThat(response.reasoningLevel()).isEqualTo("HIGH");
    }
}
