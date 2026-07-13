package com.interview.resume.domain;

import java.util.List;

public record ResumeDocument(
    int schemaVersion,
    String locale,
    Profile profile,
    String summary,
    List<Skill> skills,
    List<Experience> experiences,
    List<Project> projects,
    List<Education> education,
    List<String> extras
) {
    public static final int CURRENT_SCHEMA_VERSION = 1;

    public ResumeDocument {
        if (schemaVersion != CURRENT_SCHEMA_VERSION) {
            throw new IllegalArgumentException("不支持的简历文档版本: " + schemaVersion);
        }
        locale = locale == null || locale.isBlank() ? "zh-CN" : locale;
        summary = summary == null ? "" : summary;
        skills = immutable(skills);
        experiences = immutable(experiences);
        projects = immutable(projects);
        education = immutable(education);
        extras = immutable(extras);
    }

    private static <T> List<T> immutable(List<T> values) {
        return values == null ? List.of() : List.copyOf(values);
    }

    public record Profile(String fullName, String email, String phone, String targetRole) {
    }

    public record Skill(String name, String level) {
    }

    public record Experience(String company, String title, String start, String end, List<String> bullets) {
        public Experience {
            bullets = immutable(bullets);
        }
    }

    public record Project(String name, String role, List<String> techStack, List<String> bullets, String outcome) {
        public Project {
            techStack = immutable(techStack);
            bullets = immutable(bullets);
        }
    }

    public record Education(String school, String degree, String end) {
    }
}
