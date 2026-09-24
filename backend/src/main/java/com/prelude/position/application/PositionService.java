package com.prelude.position.application;

import com.prelude.position.domain.Position;

import java.util.List;

public interface PositionService {

    List<Position> listPositions();

    Position createPosition(String name, String systemPrompt);

    Position updatePosition(Long positionId, String name, String systemPrompt);

    void deletePosition(Long positionId);
}
