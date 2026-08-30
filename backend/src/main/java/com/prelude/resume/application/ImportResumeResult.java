package com.prelude.resume.application;

import com.prelude.resume.application.port.ResumeParser;

import java.util.List;

public record ImportResumeResult(
    Long resumeId,
    List<String> skills,
    List<ResumeParser.ParsedProject> projects
) {
}
