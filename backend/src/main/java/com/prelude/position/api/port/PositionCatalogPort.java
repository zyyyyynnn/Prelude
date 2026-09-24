package com.prelude.position.api.port;

public interface PositionCatalogPort {

    PositionSnapshot findAccessibleById(Long accountId, Long positionId);

    record PositionSnapshot(Long id, String name, String systemPrompt) {
    }
}
