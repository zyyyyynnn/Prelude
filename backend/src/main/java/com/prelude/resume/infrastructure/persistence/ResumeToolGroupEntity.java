package com.prelude.resume.infrastructure.persistence;

import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("resume_tool_group")
public class ResumeToolGroupEntity {

    private Long id;
    private Long messageId;
    private String label;
    private String status;
    private LocalDateTime createdAt;
}
