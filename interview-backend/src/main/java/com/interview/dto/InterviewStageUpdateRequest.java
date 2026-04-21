package com.interview.dto;

import jakarta.validation.constraints.NotBlank;

public record InterviewStageUpdateRequest(
    @NotBlank(message = "stageName 不能为空")
    String stageName
) {
}
