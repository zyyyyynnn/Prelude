package com.interview.insight.domain;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ScoreHistory {

    private Long id;
    private Long userId;
    private Long sessionId;
    private Integer technicalScore;
    private Integer expressionScore;
    private Integer logicScore;
    private LocalDateTime createdAt;
}
