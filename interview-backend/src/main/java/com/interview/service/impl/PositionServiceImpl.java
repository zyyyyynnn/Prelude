package com.interview.service.impl;

import com.interview.dto.PositionTemplateResponse;
import com.interview.mapper.PositionTemplateMapper;
import com.interview.service.PositionService;
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
