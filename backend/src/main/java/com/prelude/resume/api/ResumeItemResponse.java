package com.prelude.resume.api;

import java.time.LocalDateTime;

public record ResumeItemResponse(
    Long id,
    String fileName,
    LocalDateTime createdAt,
    long sessionCount,
    boolean inUse
) {
}
