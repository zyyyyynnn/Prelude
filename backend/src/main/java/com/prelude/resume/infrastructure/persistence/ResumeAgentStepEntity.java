package com.prelude.resume.infrastructure.persistence;

import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("resume_agent_step")
public class ResumeAgentStepEntity {

    private Long id;
    private Long runId;
    private Integer sortOrder;
    private String kind;
    private String label;
    private String toolName;
    private String argumentsJson;
    private String resultExcerpt;
    private String filesJson;
    private String chipsJson;
    private String detailJson;
    private String badge;
    private String badgeTone;
    private String state;
    private String error;
    private LocalDateTime createdAt;
}
