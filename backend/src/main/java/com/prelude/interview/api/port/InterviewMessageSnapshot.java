package com.prelude.interview.api.port;

import com.prelude.interview.domain.InterviewMessageLineage;
import java.time.LocalDateTime;
import java.util.List;

public record InterviewMessageSnapshot(
    Long id,
    Long sessionId,
    String role,
    String content,
    Integer seqNum,
    Integer score,
    String hint,
    String assessmentStatus,
    String assessmentError,
    Long supersededByMessageId,
    Long replyToMessageId,
    LocalDateTime createdAt
) {
    public static List<InterviewMessageSnapshot> effectiveMessages(List<InterviewMessageSnapshot> messages) {
        return InterviewMessageLineage.effective(
            messages,
            InterviewMessageSnapshot::id,
            InterviewMessageSnapshot::role,
            InterviewMessageSnapshot::supersededByMessageId,
            InterviewMessageSnapshot::replyToMessageId
        );
    }
}
