package com.prelude.resume.application.port;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ResumeRepository {

    StoredResume create(NewResume resume);

    Optional<StoredResume> findById(Long resumeId);

    List<ResumeListItem> listByOwner(Long accountId);

    boolean hasInterviewSessions(Long resumeId);

    void delete(Long resumeId);

    record NewResume(
        Long accountId,
        String fileName,
        String rawText,
        List<String> parsedSkills,
        List<ParsedProject> parsedProjects
    ) {
        public NewResume {
            parsedSkills = parsedSkills == null ? List.of() : List.copyOf(parsedSkills);
            parsedProjects = parsedProjects == null ? List.of() : List.copyOf(parsedProjects);
        }
    }

    record StoredResume(
        Long id,
        Long accountId,
        String fileName,
        String rawText,
        List<String> parsedSkills,
        List<ParsedProject> parsedProjects,
        LocalDateTime createdAt
    ) {
        public StoredResume {
            parsedSkills = parsedSkills == null ? List.of() : List.copyOf(parsedSkills);
            parsedProjects = parsedProjects == null ? List.of() : List.copyOf(parsedProjects);
        }
    }

    record ParsedProject(String name, String description) {
    }

    record ResumeListItem(
        Long id,
        String fileName,
        LocalDateTime createdAt,
        long sessionCount
    ) {
        public boolean inUse() {
            return sessionCount > 0;
        }
    }

}
