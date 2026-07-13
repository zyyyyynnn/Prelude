package com.interview.catalog.api;

import com.interview.shared.api.Result;
import com.interview.catalog.api.PositionTemplateResponse;
import com.interview.catalog.application.PositionService;
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
