package com.prelude.position.infrastructure.persistence;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.prelude.position.api.port.PositionRepository;
import com.prelude.position.domain.Position;
import java.util.List;

public interface PositionMapper extends BaseMapper<PositionEntity>, PositionRepository {

    @Override
    default List<Position> listAccessible(Long accountId) {
        return selectList(new LambdaQueryWrapper<PositionEntity>()
                .and(query -> query.isNull(PositionEntity::getAccountId)
                    .or().eq(PositionEntity::getAccountId, accountId))
                .orderByAsc(PositionEntity::getId))
            .stream()
            .map(PositionEntity::toDomain)
            .toList();
    }

    @Override
    default Position findAccessible(Long accountId, Long positionId) {
        PositionEntity entity = selectOne(new LambdaQueryWrapper<PositionEntity>()
            .eq(PositionEntity::getId, positionId)
            .and(query -> query.isNull(PositionEntity::getAccountId)
                .or().eq(PositionEntity::getAccountId, accountId))
            .last("LIMIT 1"));
        return entity == null ? null : entity.toDomain();
    }

    @Override
    default Position findOwned(Long accountId, Long positionId) {
        PositionEntity entity = selectOne(new LambdaQueryWrapper<PositionEntity>()
            .eq(PositionEntity::getId, positionId)
            .eq(PositionEntity::getAccountId, accountId)
            .last("LIMIT 1"));
        return entity == null ? null : entity.toDomain();
    }

    @Override
    default boolean nameTaken(String name, Long exceptPositionId) {
        LambdaQueryWrapper<PositionEntity> query =
            new LambdaQueryWrapper<PositionEntity>().eq(PositionEntity::getName, name);
        if (exceptPositionId != null) {
            query.ne(PositionEntity::getId, exceptPositionId);
        }
        Long count = selectCount(query);
        return count != null && count > 0;
    }

    @Override
    default void add(Position position) {
        PositionEntity entity = PositionEntity.of(position);
        insert(entity);
        position.setId(entity.getId());
    }

    @Override
    default void change(Position position) {
        updateById(PositionEntity.of(position));
    }

    @Override
    default void remove(Long positionId) {
        deleteById(positionId);
    }
}
