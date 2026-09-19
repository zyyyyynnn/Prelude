package com.prelude.position.application;

import com.prelude.BusinessException;
import com.prelude.identity.api.CurrentAccount;
import com.prelude.position.api.port.PositionRepository;
import com.prelude.position.domain.Position;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PositionServiceImpl implements PositionService {

    private final PositionRepository positions;
    private final CurrentAccount currentAccount;

    @Override
    public List<Position> listPositions() {
        return positions.listAccessible(currentAccount.requireId());
    }

    @Override
    public Position createPosition(String name, String systemPrompt) {
        Long accountId = currentAccount.requireId();
        String normalizedName = name.trim();
        if (positions.nameTaken(normalizedName, null)) {
            throw BusinessException.badRequest("同名岗位已存在");
        }
        Position position = new Position();
        position.setAccountId(accountId);
        position.setName(normalizedName);
        position.setSystemPrompt(systemPrompt.trim());
        positions.add(position);
        return position;
    }

    @Override
    public Position updatePosition(Long positionId, String name, String systemPrompt) {
        Position position = requireOwned(positionId);
        String normalizedName = name.trim();
        if (positions.nameTaken(normalizedName, positionId)) {
            throw BusinessException.badRequest("同名岗位已存在");
        }
        position.setName(normalizedName);
        position.setSystemPrompt(systemPrompt.trim());
        positions.change(position);
        return position;
    }

    @Override
    public void deletePosition(Long positionId) {
        requireOwned(positionId);
        try {
            positions.remove(positionId);
        } catch (DataIntegrityViolationException exception) {
            throw BusinessException.badRequest("该岗位已被面试使用，无法删除");
        }
    }

    private Position requireOwned(Long positionId) {
        Position position = positions.findOwned(currentAccount.requireId(), positionId);
        if (position == null) throw BusinessException.badRequest("岗位不存在或不可编辑");
        return position;
    }
}
