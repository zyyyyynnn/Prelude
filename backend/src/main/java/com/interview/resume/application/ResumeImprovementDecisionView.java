package com.interview.resume.application;

public record ResumeImprovementDecisionView(
    ResumeImprovementView improvement,
    ResumeDocumentView resume
) {
}
