package com.prelude.resume.api;

import java.util.List;

/**
 * One assistant message's run trace as the workspace stream renders it.
 *
 * <p>{@code summary} is the run header and {@code status} is only ever {@code running} or
 * {@code done}: a step that failed stays on its own row, because the run did finish — this one
 * call inside it did not.
 */
public record ResumeToolGroupResponse(
    Long id,
    String summary,
    String status,
    List<ResumeToolStepResponse> steps
) {
}
