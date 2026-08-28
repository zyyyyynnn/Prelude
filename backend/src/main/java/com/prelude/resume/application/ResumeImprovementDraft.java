package com.prelude.resume.application;

public record ResumeImprovementDraft(
    String targetPath,
    String currentText,
    String proposedText,
    String rationale,
    String evidence
) {
}
