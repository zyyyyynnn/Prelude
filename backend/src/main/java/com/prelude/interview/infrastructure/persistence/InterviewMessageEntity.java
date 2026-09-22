package com.prelude.interview.infrastructure.persistence;

import com.baomidou.mybatisplus.annotation.TableName;
import com.prelude.interview.domain.InterviewMessage;
import lombok.Data;

import java.time.LocalDateTime;

/** Row shape of {@code interview_message}; keeps the table mapping out of the domain model. */
@Data
@TableName("interview_message")
public class InterviewMessageEntity {

    private Long id;
    private Long sessionId;
    private String role;
    private String content;
    private Integer seqNum;
    private Integer score;
    private String hint;
    private LocalDateTime createdAt;

    public static InterviewMessageEntity of(InterviewMessage message) {
        InterviewMessageEntity entity = new InterviewMessageEntity();
        entity.setId(message.getId());
        entity.setSessionId(message.getSessionId());
        entity.setRole(message.getRole());
        entity.setContent(message.getContent());
        entity.setSeqNum(message.getSeqNum());
        entity.setScore(message.getScore());
        entity.setHint(message.getHint());
        entity.setCreatedAt(message.getCreatedAt());
        return entity;
    }

    public InterviewMessage toDomain() {
        InterviewMessage message = new InterviewMessage();
        message.setId(id);
        message.setSessionId(sessionId);
        message.setRole(role);
        message.setContent(content);
        message.setSeqNum(seqNum);
        message.setScore(score);
        message.setHint(hint);
        message.setCreatedAt(createdAt);
        return message;
    }
}
