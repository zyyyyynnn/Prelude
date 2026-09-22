package com.prelude.artifact.infrastructure.persistence;

import com.baomidou.mybatisplus.annotation.TableName;
import com.prelude.artifact.domain.ScoreHistory;
import lombok.Data;

import java.time.LocalDateTime;

/** Row shape of {@code score_history}; keeps the table mapping out of the domain model. */
@Data
@TableName("score_history")
public class ScoreHistoryEntity {

    private Long id;
    private Long accountId;
    private Long sessionId;
    private Integer technicalScore;
    private Integer expressionScore;
    private Integer logicScore;
    private LocalDateTime createdAt;

    public static ScoreHistoryEntity of(ScoreHistory score) {
        ScoreHistoryEntity entity = new ScoreHistoryEntity();
        entity.setId(score.getId());
        entity.setAccountId(score.getAccountId());
        entity.setSessionId(score.getSessionId());
        entity.setTechnicalScore(score.getTechnicalScore());
        entity.setExpressionScore(score.getExpressionScore());
        entity.setLogicScore(score.getLogicScore());
        entity.setCreatedAt(score.getCreatedAt());
        return entity;
    }

    public ScoreHistory toDomain() {
        ScoreHistory score = new ScoreHistory();
        score.setId(id);
        score.setAccountId(accountId);
        score.setSessionId(sessionId);
        score.setTechnicalScore(technicalScore);
        score.setExpressionScore(expressionScore);
        score.setLogicScore(logicScore);
        score.setCreatedAt(createdAt);
        return score;
    }
}
