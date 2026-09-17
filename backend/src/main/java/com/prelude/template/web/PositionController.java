package com.prelude.template.web;

import com.prelude.Result;
import com.prelude.template.api.CreatePositionRequest;
import com.prelude.template.api.PositionTemplateResponse;
import com.prelude.template.application.PositionService;
import com.prelude.template.domain.PositionTemplate;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
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
        return Result.success(positionService.listPositions().stream()
            .map(PositionController::toResponse)
            .toList());
    }

    @PostMapping
    public Result<PositionTemplateResponse> create(@jakarta.validation.Valid @RequestBody CreatePositionRequest request) {
        return Result.success(toResponse(positionService.createPosition(request.name(), request.systemPrompt())));
    }

    @PutMapping("/{positionId}")
    public Result<PositionTemplateResponse> update(
        @PathVariable Long positionId,
        @jakarta.validation.Valid @RequestBody CreatePositionRequest request
    ) {
        return Result.success(toResponse(positionService.updatePosition(
            positionId, request.name(), request.systemPrompt())));
    }

    @DeleteMapping("/{positionId}")
    public Result<Void> delete(@PathVariable Long positionId) {
        positionService.deletePosition(positionId);
        return Result.success(null);
    }

    private static PositionTemplateResponse toResponse(PositionTemplate position) {
        return new PositionTemplateResponse(
            position.getId(),
            position.getName(),
            position.getSystemPrompt(),
            position.getAccountId() != null
        );
    }
}
