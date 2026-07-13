package com.interview.resume.application;

import com.interview.resume.application.port.ResumeParser;

import java.util.List;

public record ImportResumeResult(
    Long resumeId,
    List<String> skills,
    List<ResumeParser.ParsedProject> projects
) {
}
