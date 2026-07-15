package com.interview.resume.infrastructure.persistence;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("resume_improvement")
public class ResumeImprovementRow {

    private Long id;
    private Long userId;
    private Long resumeId;
    private Long sessionId;
    private Integer ordinal;
    private String targetPath;
    private String currentText;
    private String proposedText;
    private String rationale;
    private String evidence;
    private Integer baseDocumentVersion;
    private String status;
    private Integer appliedDocumentVersion;
    private LocalDateTime createdAt;
    private LocalDateTime decidedAt;
}
