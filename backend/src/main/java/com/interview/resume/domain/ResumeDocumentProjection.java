package com.interview.resume.domain;

import java.util.List;

public record ResumeDocumentProjection(
    String plainText,
    List<String> skills,
    List<String> projectsSummary
) {
}
