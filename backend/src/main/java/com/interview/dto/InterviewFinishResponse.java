package com.interview.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class InterviewFinishResponse {

    private Long sessionId;
    private String summaryReport;
    private String status;
}
