package com.prelude.resume.infrastructure.persistence;

import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("resume_assistant_message")
public class ResumeAssistantMessageEntity {

    private Long id;
    private Long turnId;
    private Long conversationId;
    private Integer seqNum;
    private String content;
    private LocalDateTime createdAt;
}
