package com.interview.resume.application.port;

import java.util.List;

public interface ResumeParser {

    ParsedResume parse(Long userId, String rawText);

    record ParsedResume(List<String> skills, List<ParsedProject> projects) {
        public ParsedResume {
            skills = skills == null ? List.of() : List.copyOf(skills);
            projects = projects == null ? List.of() : List.copyOf(projects);
        }
    }

    record ParsedProject(String name, String description) {
    }
}
