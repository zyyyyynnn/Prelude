package com.interview.catalog.application;

import com.interview.catalog.api.PositionTemplateResponse;

import java.util.List;

public interface PositionService {

    List<PositionTemplateResponse> listPositions();
}
