package com.prelude.resume.application;

import com.prelude.resume.application.port.ResumeRepository;
import com.prelude.resume.domain.ResumeDocument;

public record ResumeDocumentView(
    Long resumeId,
    String fileName,
    int documentVersion,
    String sourceType,
    ResumeDocument document
) {
    static ResumeDocumentView from(ResumeRepository.StoredResume resume) {
        return new ResumeDocumentView(
            resume.id(), resume.fileName(), resume.documentVersion(), resume.sourceType(), resume.document()
        );
    }
}
