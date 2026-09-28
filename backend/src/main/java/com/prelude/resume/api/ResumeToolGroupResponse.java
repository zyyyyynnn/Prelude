package com.prelude.resume.api;

import java.util.List;

/** One assistant message's tool-call trace as the workspace stream renders it. */
public record ResumeToolGroupResponse(
    Long id,
    String summary,
    String status,
    List<ResumeToolStepResponse> steps
) {
}
