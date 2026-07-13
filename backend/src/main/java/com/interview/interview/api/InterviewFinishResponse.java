package com.interview.interview.api;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class InterviewFinishResponse {

    private Long sessionId;
    private String summaryReport;
    private String status;
    private String jobId;
}
