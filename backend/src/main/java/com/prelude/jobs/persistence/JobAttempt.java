package com.prelude.jobs.persistence;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("job_attempt")
public class JobAttempt {

    private Long id;
    private String jobId;
    private Integer attemptNumber;
    private String status;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private String failureSummary;

    public static final String RUNNING = "RUNNING";
    public static final String SUCCEEDED = "SUCCEEDED";
    public static final String FAILED = "FAILED";
    public static final String INTERRUPTED = "INTERRUPTED";
}
