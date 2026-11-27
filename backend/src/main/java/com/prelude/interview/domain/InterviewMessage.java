package com.prelude.interview.domain;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class InterviewMessage {

    private Long id;
    private Long sessionId;
    private String role;
    private String content;
    private Integer seqNum;
    private Integer score;
    private String hint;
    private AssessmentStatus assessmentStatus;
    private String assessmentError;
    private Long supersededByMessageId;
    private Long replyToMessageId;
    private LocalDateTime createdAt;

    public static List<InterviewMessage> effectiveMessages(List<InterviewMessage> messages) {
        return InterviewMessageLineage.effective(
            messages,
            InterviewMessage::getId,
            InterviewMessage::getRole,
            InterviewMessage::getSupersededByMessageId,
            InterviewMessage::getReplyToMessageId
        );
    }
}
