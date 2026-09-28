package com.prelude.resume.infrastructure.persistence;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("resume_tool_diff")
public class ResumeToolDiffEntity {

    private Long id;
    private Long groupId;
    private Integer sortOrder;
    private String fileName;
    private Integer addCount;
    private Integer delCount;
}
