package com.interview.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("interview_session")
public class InterviewSession {

    private Long id;
    private Long userId;
    private Long resumeId;
    private Long positionId;
    private String targetPosition;
    private String llmProvider;
    private String llmModel;
    private String status;
    private String summaryReport;
    private LocalDateTime createdAt;
}
