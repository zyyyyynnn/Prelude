package com.interview.catalog.application;

import com.interview.catalog.api.PositionTemplateResponse;
import com.interview.catalog.infrastructure.persistence.PositionTemplateMapper;
import com.interview.catalog.application.PositionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PositionServiceImpl implements PositionService {

    private final PositionTemplateMapper positionTemplateMapper;

    @Override
    public List<PositionTemplateResponse> listPositions() {
        return positionTemplateMapper.selectList(null)
            .stream()
            .map(position -> new PositionTemplateResponse(position.getId(), position.getName()))
            .toList();
    }
}
