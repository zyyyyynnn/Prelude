package com.prelude.resume.infrastructure.persistence;

import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("resume_agent_run")
public class ResumeAgentRunEntity {

    private Long id;
    private Long turnId;
    private Long conversationId;
    private Long resumeId;
    private Integer baseRevision;
    private Long modelExecutionSnapshotId;
    private String promptId;
    private String status;
    private String failureReason;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
}
