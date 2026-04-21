package com.interview.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("score_history")
public class ScoreHistory {

    private Long id;
    private Long userId;
    private Long sessionId;
    private Integer technicalScore;
    private Integer expressionScore;
    private Integer logicScore;
    private LocalDateTime createdAt;
}
