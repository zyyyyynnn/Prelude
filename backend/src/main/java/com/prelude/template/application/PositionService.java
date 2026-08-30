package com.prelude.template.application;

import com.prelude.template.api.PositionTemplateResponse;

import java.util.List;

public interface PositionService {

    List<PositionTemplateResponse> listPositions();

    PositionTemplateResponse createPosition(String name, String systemPrompt);

    PositionTemplateResponse updatePosition(Long positionId, String name, String systemPrompt);

    void deletePosition(Long positionId);
}
