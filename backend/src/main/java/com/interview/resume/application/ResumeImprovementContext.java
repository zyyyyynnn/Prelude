package com.interview.resume.application;

import java.util.List;

public record ResumeImprovementContext(
    Long resumeId,
    int documentVersion,
    List<EditableStatement> statements
) {
    public record EditableStatement(String targetPath, String currentText) {
    }
}
