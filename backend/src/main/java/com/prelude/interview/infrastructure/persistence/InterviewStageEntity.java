package com.prelude.interview.infrastructure.persistence;

import com.baomidou.mybatisplus.annotation.TableName;
import com.prelude.interview.domain.InterviewStage;
import lombok.Data;

import java.time.LocalDateTime;

/** Row shape of {@code interview_stage}; keeps the table mapping out of the domain model. */
@Data
@TableName("interview_stage")
public class InterviewStageEntity {

    private Long id;
    private Long sessionId;
    private String stageName;
    private LocalDateTime startedAt;
    private LocalDateTime endedAt;

    public static InterviewStageEntity of(InterviewStage stage) {
        InterviewStageEntity entity = new InterviewStageEntity();
        entity.setId(stage.getId());
        entity.setSessionId(stage.getSessionId());
        entity.setStageName(stage.getStageName());
        entity.setStartedAt(stage.getStartedAt());
        entity.setEndedAt(stage.getEndedAt());
        return entity;
    }

    public InterviewStage toDomain() {
        InterviewStage stage = new InterviewStage();
        stage.setId(id);
        stage.setSessionId(sessionId);
        stage.setStageName(stageName);
        stage.setStartedAt(startedAt);
        stage.setEndedAt(endedAt);
        return stage;
    }
}
