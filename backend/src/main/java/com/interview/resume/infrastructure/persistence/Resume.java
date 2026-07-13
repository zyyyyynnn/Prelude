package com.interview.resume.infrastructure.persistence;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("resume")
public class Resume {

    private Long id;
    private Long userId;
    private String fileName;
    private String parsedSkills;
    private String parsedProjects;
    private String rawText;
    private String documentJson;
    private Integer documentVersion;
    private String sourceType;
    private String plainTextProjection;
    private LocalDateTime createdAt;
}
