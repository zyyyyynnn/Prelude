package com.interview.platform.job;

import java.time.LocalDateTime;
import java.util.Optional;

public interface JobQueryPort {

    Optional<JobSnapshot> findOwned(String jobId, Long userId);

    record JobSnapshot(
        String jobId,
        String type,
        Long subjectId,
        String status,
        int attempts,
        LocalDateTime createdAt,
        LocalDateTime finishedAt
    ) {
    }
}
