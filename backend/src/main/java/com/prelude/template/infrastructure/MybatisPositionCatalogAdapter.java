package com.prelude.template.infrastructure;

import com.prelude.template.api.port.PositionCatalogPort;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.prelude.template.domain.PositionTemplate;
import com.prelude.template.infrastructure.persistence.PositionTemplateMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MybatisPositionCatalogAdapter implements PositionCatalogPort {

    private final PositionTemplateMapper positionTemplateMapper;

    @Override
    public PositionSnapshot findAccessibleById(Long accountId, Long positionId) {
        PositionTemplate position = positionTemplateMapper.selectOne(
            new LambdaQueryWrapper<PositionTemplate>()
                .eq(PositionTemplate::getId, positionId)
                .and(query -> query.isNull(PositionTemplate::getAccountId)
                    .or().eq(PositionTemplate::getAccountId, accountId))
                .last("LIMIT 1")
        );
        return position == null
            ? null
            : new PositionSnapshot(position.getId(), position.getName(), position.getSystemPrompt());
    }
}
