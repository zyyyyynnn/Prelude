package com.prelude.resume.infrastructure.persistence;

import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("resume_patch_proposal")
public class ResumePatchProposalEntity {

    private Long id;
    private Long runId;
    private Long resumeId;
    private Long conversationId;
    private Integer baseRevision;
    private String affectedBlockIds;
    private String reason;
    private String operationsJson;
    private String factRiskJson;
    private String status;
    private String decisionNote;
    private LocalDateTime decidedAt;
    private Long decidedByAccountId;
    private LocalDateTime createdAt;
}
