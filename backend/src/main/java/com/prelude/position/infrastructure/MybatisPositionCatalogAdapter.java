package com.prelude.position.infrastructure;

import com.prelude.position.api.port.PositionCatalogPort;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.prelude.position.domain.Position;
import com.prelude.position.infrastructure.persistence.PositionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MybatisPositionCatalogAdapter implements PositionCatalogPort {

    private final PositionMapper positionMapper;

    @Override
    public PositionSnapshot findAccessibleById(Long accountId, Long positionId) {
        Position position = positionMapper.selectOne(
            new LambdaQueryWrapper<Position>()
                .eq(Position::getId, positionId)
                .and(query -> query.isNull(Position::getAccountId)
                    .or().eq(Position::getAccountId, accountId))
                .last("LIMIT 1")
        );
        return position == null
            ? null
            : new PositionSnapshot(position.getId(), position.getName(), position.getSystemPrompt());
    }
}
