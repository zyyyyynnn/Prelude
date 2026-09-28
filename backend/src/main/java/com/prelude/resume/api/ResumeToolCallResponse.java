package com.prelude.resume.api;

import java.util.List;

public record ResumeToolCallResponse(
    Long id,
    String kind,
    String label,
    String chip,
    String state,
    String error,
    List<ResumeToolDetailLine> detail
) {
}
