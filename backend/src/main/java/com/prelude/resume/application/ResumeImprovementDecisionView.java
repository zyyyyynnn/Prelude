package com.prelude.resume.application;

public record ResumeImprovementDecisionView(
    ResumeImprovementView improvement,
    ResumeDocumentView resume
) {
}
