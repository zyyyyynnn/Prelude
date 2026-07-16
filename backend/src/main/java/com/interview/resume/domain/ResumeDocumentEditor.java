package com.interview.resume.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ResumeDocumentEditor {

    private static final Pattern EXPERIENCE_BULLET = Pattern.compile("experiences\\[(\\d+)]\\.bullets\\[(\\d+)]");
    private static final Pattern PROJECT_BULLET = Pattern.compile("projects\\[(\\d+)]\\.bullets\\[(\\d+)]");
    private static final Pattern PROJECT_OUTCOME = Pattern.compile("projects\\[(\\d+)]\\.outcome");

    public List<EditableStatement> statements(ResumeDocument document) {
        Objects.requireNonNull(document, "resume document must not be null");
        List<EditableStatement> statements = new ArrayList<>();
        statements.add(new EditableStatement("summary", document.summary()));
        for (int experienceIndex = 0; experienceIndex < document.experiences().size(); experienceIndex++) {
            ResumeDocument.Experience experience = document.experiences().get(experienceIndex);
            for (int bulletIndex = 0; bulletIndex < experience.bullets().size(); bulletIndex++) {
                statements.add(new EditableStatement(
                    "experiences[" + experienceIndex + "].bullets[" + bulletIndex + "]",
                    experience.bullets().get(bulletIndex)
                ));
            }
        }
        for (int projectIndex = 0; projectIndex < document.projects().size(); projectIndex++) {
            ResumeDocument.Project project = document.projects().get(projectIndex);
            for (int bulletIndex = 0; bulletIndex < project.bullets().size(); bulletIndex++) {
                statements.add(new EditableStatement(
                    "projects[" + projectIndex + "].bullets[" + bulletIndex + "]",
                    project.bullets().get(bulletIndex)
                ));
            }
            statements.add(new EditableStatement(
                "projects[" + projectIndex + "].outcome",
                text(project.outcome())
            ));
        }
        return List.copyOf(statements);
    }

    public ResumeDocument apply(
        ResumeDocument document,
        String targetPath,
        String expectedCurrentText,
        String proposedText
    ) {
        Objects.requireNonNull(document, "resume document must not be null");
        String replacement = requireText(proposedText, "建议文本不能为空");
        if (replacement.length() > 1_500) {
            throw new IllegalArgumentException("建议文本过长");
        }
        if ("summary".equals(targetPath)) {
            requireCurrent(document.summary(), expectedCurrentText);
            return copy(document, replacement, document.experiences(), document.projects());
        }

        Matcher experienceBullet = EXPERIENCE_BULLET.matcher(text(targetPath));
        if (experienceBullet.matches()) {
            int experienceIndex = Integer.parseInt(experienceBullet.group(1));
            int bulletIndex = Integer.parseInt(experienceBullet.group(2));
            List<ResumeDocument.Experience> experiences = new ArrayList<>(document.experiences());
            ResumeDocument.Experience experience = requireIndex(experiences, experienceIndex, "工作经历");
            List<String> bullets = new ArrayList<>(experience.bullets());
            requireCurrent(requireIndex(bullets, bulletIndex, "工作经历要点"), expectedCurrentText);
            bullets.set(bulletIndex, replacement);
            experiences.set(experienceIndex, new ResumeDocument.Experience(
                experience.company(), experience.title(), experience.start(), experience.end(), bullets
            ));
            return copy(document, document.summary(), experiences, document.projects());
        }

        Matcher projectBullet = PROJECT_BULLET.matcher(text(targetPath));
        if (projectBullet.matches()) {
            int projectIndex = Integer.parseInt(projectBullet.group(1));
            int bulletIndex = Integer.parseInt(projectBullet.group(2));
            List<ResumeDocument.Project> projects = new ArrayList<>(document.projects());
            ResumeDocument.Project project = requireIndex(projects, projectIndex, "项目经历");
            List<String> bullets = new ArrayList<>(project.bullets());
            requireCurrent(requireIndex(bullets, bulletIndex, "项目要点"), expectedCurrentText);
            bullets.set(bulletIndex, replacement);
            projects.set(projectIndex, copyProject(project, bullets, project.outcome()));
            return copy(document, document.summary(), document.experiences(), projects);
        }

        Matcher projectOutcome = PROJECT_OUTCOME.matcher(text(targetPath));
        if (projectOutcome.matches()) {
            int projectIndex = Integer.parseInt(projectOutcome.group(1));
            List<ResumeDocument.Project> projects = new ArrayList<>(document.projects());
            ResumeDocument.Project project = requireIndex(projects, projectIndex, "项目经历");
            requireCurrent(text(project.outcome()), expectedCurrentText);
            projects.set(projectIndex, copyProject(project, project.bullets(), replacement));
            return copy(document, document.summary(), document.experiences(), projects);
        }
        throw new IllegalArgumentException("不支持的简历字段路径");
    }

    private ResumeDocument copy(
        ResumeDocument source,
        String summary,
        List<ResumeDocument.Experience> experiences,
        List<ResumeDocument.Project> projects
    ) {
        return new ResumeDocument(
            source.schemaVersion(), source.locale(), source.profile(), summary, source.skills(),
            experiences, projects, source.education(), source.extras()
        );
    }

    private ResumeDocument.Project copyProject(
        ResumeDocument.Project source,
        List<String> bullets,
        String outcome
    ) {
        return new ResumeDocument.Project(
            source.name(), source.role(), source.techStack(), bullets, outcome
        );
    }

    private void requireCurrent(String actual, String expected) {
        if (!Objects.equals(text(actual), text(expected))) {
            throw new IllegalArgumentException("简历字段已发生变化，请重新生成建议");
        }
    }

    private <T> T requireIndex(List<T> values, int index, String label) {
        if (index < 0 || index >= values.size()) {
            throw new IllegalArgumentException(label + "已发生变化，请重新生成建议");
        }
        return values.get(index);
    }

    private String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }

    private String text(String value) {
        return value == null ? "" : value.trim();
    }

    public record EditableStatement(String targetPath, String currentText) {
    }
}
