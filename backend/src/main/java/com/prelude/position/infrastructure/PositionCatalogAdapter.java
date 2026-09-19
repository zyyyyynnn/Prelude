package com.prelude.position.infrastructure;

import com.prelude.position.api.port.PositionCatalogPort;
import com.prelude.position.api.port.PositionRepository;
import com.prelude.position.domain.Position;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PositionCatalogAdapter implements PositionCatalogPort {

    private final PositionRepository positions;

    @Override
    public PositionSnapshot findAccessibleById(Long accountId, Long positionId) {
        Position position = positions.findAccessible(accountId, positionId);
        return position == null
            ? null
            : new PositionSnapshot(position.getId(), position.getName(), position.getSystemPrompt());
    }
}
