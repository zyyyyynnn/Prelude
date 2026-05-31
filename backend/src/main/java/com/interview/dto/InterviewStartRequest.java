package com.interview.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class InterviewStartRequest {

    @NotNull(message = "请选择简历")
    private Long resumeId;

    @NotNull(message = "请选择目标岗位")
    private Long positionId;
}
