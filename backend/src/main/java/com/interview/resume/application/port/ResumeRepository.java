package com.interview.resume.application.port;

import com.interview.resume.domain.ResumeDocument;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ResumeRepository {

    StoredResume create(NewResume resume);

    Optional<StoredResume> findById(Long resumeId);

    List<ResumeListItem> listByOwner(Long userId);

    boolean hasInterviewSessions(Long resumeId);

    boolean updateDocument(
        Long resumeId,
        Long userId,
        int expectedVersion,
        ResumeDocument document,
        String sourceType
    );

    void delete(Long resumeId);

    long countWithoutDocument();

    List<LegacyResume> findWithoutDocumentAfter(long afterId, int batchSize);

    boolean backfillDocument(Long resumeId, ResumeDocument document);

    record NewResume(
        Long userId,
        String fileName,
        String rawText,
        ResumeDocument document,
        String sourceType
    ) {
    }

    record StoredResume(
        Long id,
        Long userId,
        String fileName,
        String rawText,
        ResumeDocument document,
        int documentVersion,
        String sourceType,
        LocalDateTime createdAt
    ) {
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

    record LegacyResume(
        Long id,
        String rawText,
        List<String> skills,
        List<LegacyProject> projects
    ) {
    }

    record LegacyProject(String name, String description) {
    }
}
