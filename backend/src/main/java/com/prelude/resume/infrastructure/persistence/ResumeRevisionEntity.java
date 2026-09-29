package com.prelude.resume.infrastructure.persistence;

import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("resume_revision")
public class ResumeRevisionEntity {

    private Long id;
    private Long resumeId;
    private Integer revisionNumber;
    private String documentJson;
    private String summary;
    private String origin;
    private Long restoredFromRevisionId;
    private Long proposalId;
    private LocalDateTime createdAt;
}
