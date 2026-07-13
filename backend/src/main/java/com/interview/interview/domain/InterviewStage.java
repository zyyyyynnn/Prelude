package com.interview.interview.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("interview_stage")
public class InterviewStage {

    private Long id;
    private Long sessionId;
    private String stageName;
    private LocalDateTime startedAt;
    private LocalDateTime endedAt;
}
