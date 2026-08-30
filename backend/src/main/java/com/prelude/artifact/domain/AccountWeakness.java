package com.prelude.artifact.domain;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AccountWeakness {

    private Long id;
    private Long accountId;
    private Long sessionId;
    private String category;
    private String description;
    private LocalDateTime createdAt;
}
