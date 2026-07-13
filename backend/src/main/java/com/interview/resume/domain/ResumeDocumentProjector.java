package com.interview.resume.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public final class ResumeDocumentProjector {

    public ResumeDocumentProjection project(ResumeDocument document) {
        List<String> sections = new ArrayList<>();
        addProfile(sections, document.profile());
        addTextSection(sections, "职业概述", document.summary());
        addSection(sections, "技能", document.skills(), this::skillLine);
        addSection(sections, "工作经历", document.experiences(), this::experienceBlock);
        addSection(sections, "项目经历", document.projects(), this::projectBlock);
        addSection(sections, "教育经历", document.education(), this::educationLine);
        addSection(sections, "其他", document.extras(), ResumeDocumentProjector::clean);

        List<String> skills = document.skills().stream()
            .map(ResumeDocument.Skill::name)
            .filter(ResumeDocumentProjector::present)
            .map(String::trim)
            .toList();
        List<String> projects = document.projects().stream()
            .map(this::projectSummary)
            .filter(ResumeDocumentProjector::present)
            .toList();
        return new ResumeDocumentProjection(String.join("\n\n", sections), skills, projects);
    }

    private void addProfile(List<String> sections, ResumeDocument.Profile profile) {
        if (profile == null) {
            return;
        }
        List<String> lines = new ArrayList<>();
        addLabeled(lines, "姓名", profile.fullName());
        addLabeled(lines, "邮箱", profile.email());
        addLabeled(lines, "电话", profile.phone());
        addLabeled(lines, "目标岗位", profile.targetRole());
        addLines(sections, "基本信息", lines);
    }

    private void addTextSection(List<String> sections, String title, String value) {
        if (present(value)) {
            sections.add(title + "\n" + value.trim());
        }
    }

    private <T> void addSection(List<String> sections, String title, List<T> values, Function<T, String> mapper) {
        List<String> lines = values.stream()
            .map(mapper)
            .filter(ResumeDocumentProjector::present)
            .toList();
        addLines(sections, title, lines);
    }

    private void addLines(List<String> sections, String title, List<String> lines) {
        if (!lines.isEmpty()) {
            sections.add(title + "\n" + String.join("\n", lines));
        }
    }

    private void addLabeled(List<String> lines, String label, String value) {
        if (present(value)) {
            lines.add(label + "：" + value.trim());
        }
    }

    private String skillLine(ResumeDocument.Skill skill) {
        if (skill == null || !present(skill.name())) {
            return "";
        }
        return skill.name().trim() + (present(skill.level()) ? "（" + skill.level().trim() + "）" : "");
    }

    private String experienceBlock(ResumeDocument.Experience experience) {
        if (experience == null) {
            return "";
        }
        List<String> header = compact(experience.company(), experience.title(), period(experience.start(), experience.end()));
        List<String> lines = new ArrayList<>();
        if (!header.isEmpty()) {
            lines.add(String.join(" · ", header));
        }
        experience.bullets().stream().filter(ResumeDocumentProjector::present)
            .map(String::trim).map(value -> "- " + value).forEach(lines::add);
        return String.join("\n", lines);
    }

    private String projectBlock(ResumeDocument.Project project) {
        if (project == null) {
            return "";
        }
        List<String> lines = new ArrayList<>();
        List<String> header = compact(project.name(), project.role());
        if (!header.isEmpty()) {
            lines.add(String.join(" · ", header));
        }
        List<String> stack = project.techStack().stream().filter(ResumeDocumentProjector::present)
            .map(String::trim).toList();
        if (!stack.isEmpty()) {
            lines.add("技术栈：" + String.join("、", stack));
        }
        project.bullets().stream().filter(ResumeDocumentProjector::present)
            .map(String::trim).map(value -> "- " + value).forEach(lines::add);
        if (present(project.outcome())) {
            lines.add("成果：" + project.outcome().trim());
        }
        return String.join("\n", lines);
    }

    private String projectSummary(ResumeDocument.Project project) {
        if (project == null || !present(project.name())) {
            return "";
        }
        List<String> details = new ArrayList<>();
        project.bullets().stream().filter(ResumeDocumentProjector::present)
            .map(String::trim).forEach(details::add);
        if (present(project.outcome())) {
            details.add(project.outcome().trim());
        }
        return project.name().trim() + (details.isEmpty() ? "" : "：" + String.join("；", details));
    }

    private String educationLine(ResumeDocument.Education education) {
        return education == null ? "" : String.join(" · ", compact(
            education.school(), education.degree(), education.end()
        ));
    }

    private String period(String start, String end) {
        if (!present(start)) {
            return clean(end);
        }
        return present(end) ? start.trim() + " - " + end.trim() : start.trim();
    }

    private static List<String> compact(String... values) {
        return java.util.Arrays.stream(values).filter(ResumeDocumentProjector::present).map(String::trim).toList();
    }

    private static boolean present(String value) {
        return value != null && !value.isBlank();
    }

    private static String clean(String value) {
        return present(value) ? value.trim() : "";
    }
}
