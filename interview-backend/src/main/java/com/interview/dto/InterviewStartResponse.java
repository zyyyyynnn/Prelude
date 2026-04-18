package com.interview.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class InterviewStartResponse {

    private Long sessionId;
    private String targetPosition;
}
