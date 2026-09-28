package com.prelude.resume.infrastructure.persistence;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("resume_tool_call")
public class ResumeToolCallEntity {

    private Long id;
    private Long groupId;
    private Integer sortOrder;
    private String kind;
    private String label;
    private String chip;
    private String detail;
    private String state;
    private String error;
}
