package com.prelude.interview.web;

import com.prelude.interview.api.InterviewMessagesResponse;
import com.prelude.interview.api.InterviewStartRequest;
import com.prelude.interview.application.InterviewSessionDetails;
import com.prelude.interview.application.StartInterviewCommand;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class InterviewApiMapperTest {

    @Test
    void mapsTheComposerModelAndReasoningIntoTheStartCommand() {
        InterviewStartRequest request = new InterviewStartRequest();
        request.setResumeId(3L);
        request.setPositionId(5L);
        request.setJdText("jd");
        request.setModel("deepseek-v4-flash");
        request.setReasoningLevel("HIGH");
        request.setAttachmentIds(List.of(8L));

        assertThat(InterviewApiMapper.toCommand(request)).isEqualTo(new StartInterviewCommand(
            3L, 5L, "jd", "deepseek-v4-flash", "HIGH", List.of(8L)));
    }

    @Test
    void exposesAssessmentStateAndError() {
        InterviewSessionDetails details = new InterviewSessionDetails(
            42L, "前端工程师", "ongoing", "technical", "model", "HIGH", null,
            List.of(),
            List.of(new com.prelude.interview.application.InterviewMessageView(
                9L, "user", "回答", 2, null, null, null,
                com.prelude.interview.domain.AssessmentStatus.UNSCORED, "model unavailable")),
            3L, 5L, null, List.of());

        var message = InterviewApiMapper.toResponse(details).messages().getFirst();

        assertThat(message.assessmentStatus()).isEqualTo(com.prelude.interview.domain.AssessmentStatus.UNSCORED);
        assertThat(message.assessmentError()).isEqualTo("model unavailable");
        assertThat(message.score()).isNull();
    }

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
