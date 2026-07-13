package com.interview.catalog.api.port;

public interface PositionCatalogPort {

    PositionSnapshot findById(Long positionId);

    record PositionSnapshot(Long id, String name, String systemPrompt) {
    }
}
