package com.interview.interview.domain;

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
    private String promptVersionsJson;
    private String status;
    private String summary;
    private String summaryReport;
    private String jdText;
    private LocalDateTime createdAt;
}
