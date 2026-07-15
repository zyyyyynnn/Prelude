package com.interview.interview.domain;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class InterviewMessage {

    private Long id;
    private Long sessionId;
    private String role;
    private String content;
    private Integer seqNum;
    private Integer score;
    private String hint;
    private LocalDateTime createdAt;
}
