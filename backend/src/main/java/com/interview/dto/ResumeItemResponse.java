package com.interview.dto;

import java.time.LocalDateTime;

public record ResumeItemResponse(
    Long id,
    String fileName,
    LocalDateTime createdAt,
    long sessionCount,
    boolean inUse
) {
}
