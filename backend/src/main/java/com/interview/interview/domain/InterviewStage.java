package com.interview.interview.domain;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class InterviewStage {

    private Long id;
    private Long sessionId;
    private String stageName;
    private LocalDateTime startedAt;
    private LocalDateTime endedAt;
}
