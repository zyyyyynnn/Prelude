package com.prelude.resume.infrastructure.persistence;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("resume")
public class Resume {

    private Long id;
    private Long accountId;
    private String fileName;
    private String parsedSkills;
    private String parsedProjects;
    private String rawText;
    private LocalDateTime createdAt;
}
