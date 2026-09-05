package com.prelude.jobs.persistence;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("background_job")
public class BackgroundJob {

    private Long id;
    private String jobId;
    private String type;
    private Long accountId;
    private Long subjectId;
    private String operationKey;
    private String payloadJson;
    private String status;
    private Integer attemptCount;
    private Integer maxAttempts;
    private String lastError;
    private LocalDateTime claimedAt;
    private LocalDateTime leaseExpiresAt;
    private LocalDateTime finishedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static final String PENDING = "PENDING";
    public static final String RUNNING = "RUNNING";
    public static final String SUCCEEDED = "SUCCEEDED";
    public static final String FAILED = "FAILED";
    public static final String CANCELLED = "CANCELLED";
}
