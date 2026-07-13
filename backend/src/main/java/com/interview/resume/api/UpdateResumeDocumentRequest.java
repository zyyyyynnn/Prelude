package com.interview.resume.api;

import com.interview.resume.domain.ResumeDocument;

public record UpdateResumeDocumentRequest(
    int expectedVersion,
    ResumeDocument document
) {
}
