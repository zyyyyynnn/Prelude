package com.prelude.position.infrastructure.persistence;

import com.baomidou.mybatisplus.annotation.TableName;
import com.prelude.position.domain.Position;
import lombok.Data;

/** Row shape of {@code position_template}; keeps the table mapping out of the domain model. */
@Data
@TableName("position_template")
public class PositionEntity {

    private Long id;
    private Long accountId;
    private String name;
    private String systemPrompt;

    static PositionEntity of(Position position) {
        PositionEntity entity = new PositionEntity();
        entity.setId(position.getId());
        entity.setAccountId(position.getAccountId());
        entity.setName(position.getName());
        entity.setSystemPrompt(position.getSystemPrompt());
        return entity;
    }

    Position toDomain() {
        Position position = new Position();
        position.setId(id);
        position.setAccountId(accountId);
        position.setName(name);
        position.setSystemPrompt(systemPrompt);
        return position;
    }
}
