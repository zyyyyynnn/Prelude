package com.prelude.interview.application;

import java.util.List;

public record StartInterviewCommand(
    Long resumeId,
    Long positionId,
    String jdText,
    String model,
    String reasoningLevel,
    List<Long> attachmentIds
) {
}
