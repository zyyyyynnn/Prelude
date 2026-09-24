package com.prelude.interview.infrastructure.persistence;

import com.baomidou.mybatisplus.annotation.TableName;
import com.prelude.interview.domain.InterviewSession;
import lombok.Data;

import java.time.LocalDateTime;

/** Row shape of {@code interview_session}; keeps the table mapping out of the domain model. */
@Data
@TableName("interview_session")
public class InterviewSessionEntity {

    private Long id;
    private Long accountId;
    private Long resumeId;
    private Long positionId;
    private String targetPosition;
    private Long modelExecutionSnapshotId;
    private String status;
    private String summary;
    private String summaryReport;
    private String jdText;
    private LocalDateTime pinnedAt;
    private LocalDateTime createdAt;

    public static InterviewSessionEntity of(InterviewSession session) {
        InterviewSessionEntity entity = new InterviewSessionEntity();
        entity.setId(session.getId());
        entity.setAccountId(session.getAccountId());
        entity.setResumeId(session.getResumeId());
        entity.setPositionId(session.getPositionId());
        entity.setTargetPosition(session.getTargetPosition());
        entity.setModelExecutionSnapshotId(session.getModelExecutionSnapshotId());
        entity.setStatus(session.getStatus());
        entity.setSummary(session.getSummary());
        entity.setSummaryReport(session.getSummaryReport());
        entity.setJdText(session.getJdText());
        entity.setPinnedAt(session.getPinnedAt());
        entity.setCreatedAt(session.getCreatedAt());
        return entity;
    }

    public InterviewSession toDomain() {
        InterviewSession session = new InterviewSession();
        session.setId(id);
        session.setAccountId(accountId);
        session.setResumeId(resumeId);
        session.setPositionId(positionId);
        session.setTargetPosition(targetPosition);
        session.setModelExecutionSnapshotId(modelExecutionSnapshotId);
        session.setStatus(status);
        session.setSummary(summary);
        session.setSummaryReport(summaryReport);
        session.setJdText(jdText);
        session.setPinnedAt(pinnedAt);
        session.setCreatedAt(createdAt);
        return session;
    }
}
