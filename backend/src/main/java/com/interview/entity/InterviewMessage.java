package com.interview.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("interview_message")
public class InterviewMessage {

    private Long id;
    private Long sessionId;
    private String role;
    private String content;
    private Integer seqNum;
    private LocalDateTime createdAt;
}
