package com.interview.resume.api.port;

import java.util.List;

public record ResumeProjection(
    Long resumeId,
    Long ownerUserId,
    String displayName,
    String plainText,
    List<String> skills,
    List<String> projectsSummary,
    Integer documentVersion
) {
}
