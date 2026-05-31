package com.interview.entity;

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
    private LocalDateTime createdAt;
}
