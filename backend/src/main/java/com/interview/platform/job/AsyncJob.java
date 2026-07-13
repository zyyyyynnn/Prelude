package com.interview.platform.job;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("async_job")
public class AsyncJob {

    private Long id;
    private String jobId;
    private String type;
    private Long userId;
    private Long subjectId;
    private String idempotencyKey;
    private String status;
    private Integer attempts;
    private String payloadJson;
    private String lastError;
    private LocalDateTime dispatchedAt;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
