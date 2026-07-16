package com.interview.resume.application;

import com.interview.resume.domain.ResumeImprovement;

public record ResumeImprovementView(
    Long id,
    Long resumeId,
    Long sessionId,
    String targetPath,
    String currentText,
    String proposedText,
    String rationale,
    String evidence,
    int baseDocumentVersion,
    String status,
    Integer appliedDocumentVersion
) {
    public static ResumeImprovementView from(ResumeImprovement improvement) {
        return new ResumeImprovementView(
            improvement.id(), improvement.resumeId(), improvement.sessionId(), improvement.targetPath(),
            improvement.currentText(), improvement.proposedText(), improvement.rationale(),
            improvement.evidence(), improvement.baseDocumentVersion(), improvement.status(),
            improvement.appliedDocumentVersion()
        );
    }
}
