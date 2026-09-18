package com.prelude.position.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.prelude.BusinessException;
import com.prelude.identity.api.CurrentAccount;
import com.prelude.position.domain.Position;
import com.prelude.position.infrastructure.persistence.PositionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PositionServiceImpl implements PositionService {

    private final PositionMapper positionMapper;
    private final CurrentAccount currentAccount;

    @Override
    public List<Position> listPositions() {
        Long accountId = currentAccountId();
        return positionMapper.selectList(new LambdaQueryWrapper<Position>()
            .and(query -> query.isNull(Position::getAccountId)
                .or().eq(Position::getAccountId, accountId))
            .orderByAsc(Position::getId));
    }

    @Override
    public Position createPosition(String name, String systemPrompt) {
        Long accountId = currentAccountId();
        String normalizedName = name.trim();
        Long count = positionMapper.selectCount(new LambdaQueryWrapper<Position>()
            .eq(Position::getName, normalizedName));
        if (count != null && count > 0) {
            throw BusinessException.badRequest("同名岗位已存在");
        }
        Position position = new Position();
        position.setAccountId(accountId);
        position.setName(normalizedName);
        position.setSystemPrompt(systemPrompt.trim());
        positionMapper.insert(position);
        return position;
    }

    @Override
    public Position updatePosition(Long positionId, String name, String systemPrompt) {
        Long accountId = currentAccountId();
        Position position = requireOwned(accountId, positionId);
        String normalizedName = name.trim();
        Long count = positionMapper.selectCount(new LambdaQueryWrapper<Position>()
            .eq(Position::getName, normalizedName)
            .ne(Position::getId, positionId));
        if (count != null && count > 0) throw BusinessException.badRequest("同名岗位已存在");
        position.setName(normalizedName);
        position.setSystemPrompt(systemPrompt.trim());
        positionMapper.updateById(position);
        return position;
    }

    @Override
    public void deletePosition(Long positionId) {
        Long accountId = currentAccountId();
        requireOwned(accountId, positionId);
        try {
            positionMapper.deleteById(positionId);
        } catch (DataIntegrityViolationException exception) {
            throw BusinessException.badRequest("该岗位已被面试使用，无法删除");
        }
    }

    private Position requireOwned(Long accountId, Long positionId) {
        Position position = positionMapper.selectOne(
            new LambdaQueryWrapper<Position>()
                .eq(Position::getId, positionId)
                .eq(Position::getAccountId, accountId)
                .last("LIMIT 1")
        );
        if (position == null) throw BusinessException.badRequest("岗位不存在或不可编辑");
        return position;
    }

    private Long currentAccountId() {
        return currentAccount.requireId();
    }
}
