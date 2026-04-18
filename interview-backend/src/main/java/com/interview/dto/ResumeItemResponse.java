package com.interview.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
public class ResumeItemResponse {

    private Long id;
    private String fileName;
    private LocalDateTime createdAt;
}
