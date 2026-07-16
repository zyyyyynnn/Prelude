package com.interview.resume.api.port;

import java.util.List;

public interface ResumeImprovementPort {

    ImprovementContext requireContext(Long userId, Long resumeId);

    List<StoredSuggestion> storeSuggestions(
        Long userId,
        Long resumeId,
        Long sessionId,
        List<SuggestionDraft> suggestions
    );

    record ImprovementContext(
        Long resumeId,
        int documentVersion,
        List<EditableStatement> statements
    ) {
    }

    record EditableStatement(String targetPath, String currentText) {
    }

    record SuggestionDraft(
        String targetPath,
        String currentText,
        String proposedText,
        String rationale,
        String evidence
    ) {
    }

    record StoredSuggestion(
        Long id,
        Long resumeId,
        Long sessionId,
        String targetPath,
        String currentText,
        String proposedText,
        String rationale,
        String evidence,
        int baseDocumentVersion,
        String status
    ) {
    }
}
