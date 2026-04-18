package com.interview.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
public class InterviewSessionItemResponse {

    private Long id;
    private String targetPosition;
    private String status;
    private LocalDateTime createdAt;
}
