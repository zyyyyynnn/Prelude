package com.prelude.artifact.domain;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ScoreHistory {

    private Long id;
    private Long accountId;
    private Long sessionId;
    private Integer technicalScore;
    private Integer expressionScore;
    private Integer logicScore;
    private LocalDateTime createdAt;
}
