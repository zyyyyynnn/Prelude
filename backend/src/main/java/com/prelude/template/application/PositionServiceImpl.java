package com.prelude.template.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.prelude.BusinessException;
import com.prelude.identity.api.CurrentAccount;
import com.prelude.template.api.PositionTemplateResponse;
import com.prelude.template.domain.PositionTemplate;
import com.prelude.template.infrastructure.persistence.PositionTemplateMapper;
import com.prelude.template.application.PositionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PositionServiceImpl implements PositionService {

    private final PositionTemplateMapper positionTemplateMapper;
    private final CurrentAccount currentAccount;

    @Override
    public List<PositionTemplateResponse> listPositions() {
        Long accountId = currentAccountId();
        return positionTemplateMapper.selectList(new LambdaQueryWrapper<PositionTemplate>()
                .and(query -> query.isNull(PositionTemplate::getAccountId)
                    .or().eq(PositionTemplate::getAccountId, accountId))
                .orderByAsc(PositionTemplate::getId))
            .stream()
            .map(position -> new PositionTemplateResponse(
                position.getId(), position.getName(), position.getSystemPrompt(),
                accountId.equals(position.getAccountId())))
            .toList();
    }

    @Override
    public PositionTemplateResponse createPosition(String name, String systemPrompt) {
        Long accountId = currentAccountId();
        String normalizedName = name.trim();
        Long count = positionTemplateMapper.selectCount(new LambdaQueryWrapper<PositionTemplate>()
            .eq(PositionTemplate::getName, normalizedName));
        if (count != null && count > 0) {
            throw BusinessException.badRequest("同名岗位已存在");
        }
        PositionTemplate position = new PositionTemplate();
        position.setAccountId(accountId);
        position.setName(normalizedName);
        position.setSystemPrompt(systemPrompt.trim());
        positionTemplateMapper.insert(position);
        return new PositionTemplateResponse(
            position.getId(), position.getName(), position.getSystemPrompt(), true);
    }

    @Override
    public PositionTemplateResponse updatePosition(Long positionId, String name, String systemPrompt) {
        Long accountId = currentAccountId();
        PositionTemplate position = requireOwned(accountId, positionId);
        String normalizedName = name.trim();
        Long count = positionTemplateMapper.selectCount(new LambdaQueryWrapper<PositionTemplate>()
            .eq(PositionTemplate::getName, normalizedName)
            .ne(PositionTemplate::getId, positionId));
        if (count != null && count > 0) throw BusinessException.badRequest("同名岗位已存在");
        position.setName(normalizedName);
        position.setSystemPrompt(systemPrompt.trim());
        positionTemplateMapper.updateById(position);
        return new PositionTemplateResponse(
            position.getId(), position.getName(), position.getSystemPrompt(), true);
    }

    @Override
    public void deletePosition(Long positionId) {
        Long accountId = currentAccountId();
        requireOwned(accountId, positionId);
        try {
            positionTemplateMapper.deleteById(positionId);
        } catch (DataIntegrityViolationException exception) {
            throw BusinessException.badRequest("该岗位已被面试使用，无法删除");
        }
    }

    private PositionTemplate requireOwned(Long accountId, Long positionId) {
        PositionTemplate position = positionTemplateMapper.selectOne(
            new LambdaQueryWrapper<PositionTemplate>()
                .eq(PositionTemplate::getId, positionId)
                .eq(PositionTemplate::getAccountId, accountId)
                .last("LIMIT 1")
        );
        if (position == null) throw BusinessException.badRequest("岗位不存在或不可编辑");
        return position;
    }

    private Long currentAccountId() {
        return currentAccount.requireId();
    }
}
