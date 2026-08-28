package com.prelude.resume.application.port;

import com.prelude.resume.domain.ResumeDocument;

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

}
