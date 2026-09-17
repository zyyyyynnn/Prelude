package com.prelude.template.application;

import com.prelude.template.domain.PositionTemplate;

import java.util.List;

public interface PositionService {

    List<PositionTemplate> listPositions();

    PositionTemplate createPosition(String name, String systemPrompt);

    PositionTemplate updatePosition(Long positionId, String name, String systemPrompt);

    void deletePosition(Long positionId);
}
