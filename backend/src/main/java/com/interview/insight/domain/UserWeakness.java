package com.interview.insight.domain;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class UserWeakness {

    private Long id;
    private Long userId;
    private Long sessionId;
    private String category;
    private String description;
    private LocalDateTime createdAt;
}
