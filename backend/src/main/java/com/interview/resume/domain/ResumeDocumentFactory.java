package com.interview.resume.domain;

import java.util.List;

public final class ResumeDocumentFactory {

    private ResumeDocumentFactory() {
    }

    public static ResumeDocument fromImport(
        String rawText,
        List<String> skills,
        List<ImportedProject> projects
    ) {
        List<ResumeDocument.Skill> documentSkills = safe(skills).stream()
            .filter(ResumeDocumentFactory::present)
            .map(name -> new ResumeDocument.Skill(name.trim(), "proficient"))
            .toList();
        List<ResumeDocument.Project> documentProjects = safe(projects).stream()
            .filter(java.util.Objects::nonNull)
            .map(project -> new ResumeDocument.Project(
                clean(project.name()),
                "",
                List.of(),
                present(project.description()) ? List.of(project.description().trim()) : List.of(),
                ""
            ))
            .toList();
        return new ResumeDocument(
            ResumeDocument.CURRENT_SCHEMA_VERSION,
            "zh-CN",
            null,
            "",
            documentSkills,
            List.of(),
            documentProjects,
            List.of(),
            present(rawText) ? List.of(rawText.trim()) : List.of()
        );
    }

    private static <T> List<T> safe(List<T> values) {
        return values == null ? List.of() : values;
    }

    private static boolean present(String value) {
        return value != null && !value.isBlank();
    }

    private static String clean(String value) {
        return present(value) ? value.trim() : "";
    }

    public record ImportedProject(String name, String description) {
    }
}
