package com.prelude.position.application.port;

import com.prelude.position.domain.Position;

import java.util.List;

/**
 * Write-side storage for position templates. Deliberately outside
 * {@code position.api.port}, which is exported as the read-only
 * {@code position::catalog} named interface: other modules read positions through
 * {@code PositionCatalogPort} and must never reach the mutating operations.
 */
public interface PositionRepository {

    /** Built-in rows plus the caller's own rows, oldest first. */
    List<Position> listAccessible(Long accountId);

    Position findAccessible(Long accountId, Long positionId);

    Position findOwned(Long accountId, Long positionId);

    boolean nameTaken(String name, Long exceptPositionId);

    void add(Position position);

    void change(Position position);

    void remove(Long positionId);
}
