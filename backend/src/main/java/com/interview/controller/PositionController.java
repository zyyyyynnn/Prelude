package com.interview.controller;

import com.interview.common.Result;
import com.interview.dto.PositionTemplateResponse;
import com.interview.service.PositionService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/position")
@RequiredArgsConstructor
public class PositionController {

    private final PositionService positionService;

    @GetMapping("/list")
    public Result<List<PositionTemplateResponse>> list() {
        return Result.success(positionService.listPositions());
    }
}
