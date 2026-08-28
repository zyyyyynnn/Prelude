package com.prelude.template.api.port;

public interface PositionCatalogPort {

    PositionSnapshot findAccessibleById(Long userId, Long positionId);

    record PositionSnapshot(Long id, String name, String systemPrompt) {
    }
}
