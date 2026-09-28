package com.prelude.resume.api;

import java.util.List;

public record ResumeToolStepResponse(
    Long id,
    String icon,
    String text,
    String badge,
    String badgeTone,
    List<String> chips,
    List<String> detail
) {
}
