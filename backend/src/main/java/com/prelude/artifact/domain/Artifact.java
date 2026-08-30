package com.prelude.artifact.domain;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class Artifact {

    private Long id;
    private Long accountId;
    private String kind;
    private LocalDateTime createdAt;
}
