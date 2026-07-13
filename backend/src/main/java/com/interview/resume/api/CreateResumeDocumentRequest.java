package com.interview.resume.api;

import com.interview.resume.domain.ResumeDocument;

public record CreateResumeDocumentRequest(
    String fileName,
    ResumeDocument document
) {
}
