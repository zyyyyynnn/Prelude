package com.interview.catalog.infrastructure;

import com.interview.catalog.api.port.PositionCatalogPort;
import com.interview.catalog.domain.PositionTemplate;
import com.interview.catalog.infrastructure.persistence.PositionTemplateMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MybatisPositionCatalogAdapter implements PositionCatalogPort {

    private final PositionTemplateMapper positionTemplateMapper;

    @Override
    public PositionSnapshot findById(Long positionId) {
        PositionTemplate position = positionTemplateMapper.selectById(positionId);
        return position == null
            ? null
            : new PositionSnapshot(position.getId(), position.getName(), position.getSystemPrompt());
    }
}
