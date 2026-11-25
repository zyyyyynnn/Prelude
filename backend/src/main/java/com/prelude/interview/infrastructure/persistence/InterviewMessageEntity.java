package com.prelude.interview.infrastructure.persistence;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.prelude.interview.domain.AssessmentStatus;
import com.prelude.interview.domain.InterviewMessage;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("interview_message")
public class InterviewMessageEntity {

    private Long id;
    private Long sessionId;
    private String role;
    private String content;
    private Integer seqNum;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Integer score;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String hint;
    @TableField(insertStrategy = FieldStrategy.ALWAYS, updateStrategy = FieldStrategy.ALWAYS)
    private AssessmentStatus assessmentStatus;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String assessmentError;
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
        entity.setAssessmentStatus(message.getAssessmentStatus() != null
            ? message.getAssessmentStatus()
            : "user".equals(message.getRole()) ? AssessmentStatus.PENDING : null);
        entity.setAssessmentError(message.getAssessmentError());
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
        message.setAssessmentStatus(assessmentStatus);
        message.setAssessmentError(assessmentError);
        message.setCreatedAt(createdAt);
        return message;
    }
}
