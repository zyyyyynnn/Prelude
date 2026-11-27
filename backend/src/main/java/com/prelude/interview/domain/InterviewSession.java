package com.prelude.interview.domain;

import com.prelude.interview.integration.InterviewSessionStatus;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class InterviewSession {

    private Long id;
    private Long accountId;
    private Long resumeId;
    private Long positionId;
    private String targetPosition;
    private Long modelExecutionSnapshotId;
    private InterviewSessionStatus status;
    private String summary;
    private String summaryReport;
    private String jdText;
    private LocalDateTime pinnedAt;
    private LocalDateTime createdAt;
}
