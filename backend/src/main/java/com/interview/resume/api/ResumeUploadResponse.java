package com.interview.resume.api;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class ResumeUploadResponse {

    private Long resumeId;
    private List<String> skills;
    private List<ResumeProjectDto> projects;
}
