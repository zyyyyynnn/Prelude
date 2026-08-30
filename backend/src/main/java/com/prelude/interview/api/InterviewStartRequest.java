package com.prelude.interview.api;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class InterviewStartRequest {

    @NotNull(message = "请选择简历")
    private Long resumeId;

    @NotNull(message = "请选择目标岗位")
    private Long positionId;

    private String jdText;

    private String llmModel;

    private List<Long> attachmentIds;
}
