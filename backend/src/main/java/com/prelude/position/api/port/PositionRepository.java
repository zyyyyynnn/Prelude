package com.prelude.position.api.port;

import com.prelude.position.domain.Position;

import java.util.List;

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
