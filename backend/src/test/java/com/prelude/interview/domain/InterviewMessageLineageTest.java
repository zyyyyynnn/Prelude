package com.prelude.interview.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class InterviewMessageLineageTest {

    @Test
    void removesSupersededAnswersAndOnlyTheirAssistantReplies() {
        InterviewMessage question = message(1L, "assistant", null);
        InterviewMessage oldAnswer = message(2L, "user", null);
        oldAnswer.setSupersededByMessageId(3L);
        InterviewMessage oldFollowUp = message(4L, "assistant", 2L);
        InterviewMessage newAnswer = message(3L, "user", null);
        InterviewMessage newFollowUp = message(5L, "assistant", 3L);
        InterviewMessage legacyReply = message(6L, "assistant", 99L);

        List<InterviewMessage> effective = InterviewMessage.effectiveMessages(
            List.of(question, oldAnswer, oldFollowUp, newAnswer, newFollowUp, legacyReply));

        assertThat(effective).extracting(InterviewMessage::getId).containsExactly(1L, 3L, 5L, 6L);
    }

    private InterviewMessage message(Long id, String role, Long replyToMessageId) {
        InterviewMessage message = new InterviewMessage();
        message.setId(id);
        message.setRole(role);
        message.setReplyToMessageId(replyToMessageId);
        return message;
    }
}
