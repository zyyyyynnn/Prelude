package com.prelude.resume.infrastructure.persistence;

import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("resume_turn")
public class ResumeTurnEntity {

    private Long id;
    private Long conversationId;
    private Long accountId;
    private String instruction;
    private String status;
    private Integer queuePosition;
    private LocalDateTime createdAt;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
}
