package com.interview.resume.domain;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ResumeDocumentEditorTest {

    private final ResumeDocumentEditor editor = new ResumeDocumentEditor();

    @Test
    void appliesWhitelistedProjectBulletWithoutChangingOtherFields() {
        ResumeDocument source = document();

        ResumeDocument updated = editor.apply(
            source,
            "projects[0].bullets[0]",
            "负责接口开发",
            "负责核心接口开发，将 P95 延迟从 420ms 降至 180ms"
        );

        assertThat(updated.projects().getFirst().bullets())
            .containsExactly("负责核心接口开发，将 P95 延迟从 420ms 降至 180ms");
        assertThat(updated.skills()).isEqualTo(source.skills());
    }

    @Test
    void rejectsStaleCurrentTextAndUnknownPaths() {
        assertThatThrownBy(() -> editor.apply(
            document(), "projects[0].bullets[0]", "旧值", "新值"
        )).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("发生变化");

        assertThatThrownBy(() -> editor.apply(
            document(), "profile.email", "a@example.com", "b@example.com"
        )).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("不支持");
    }

    private ResumeDocument document() {
        return new ResumeDocument(
            1,
            "zh-CN",
            new ResumeDocument.Profile("张三", "a@example.com", "13800000000", "Java 工程师"),
            "后端工程师",
            List.of(new ResumeDocument.Skill("Java", "proficient")),
            List.of(),
            List.of(new ResumeDocument.Project(
                "Prelude", "后端开发", List.of("Spring Boot"), List.of("负责接口开发"), ""
            )),
            List.of(),
            List.of()
        );
    }
}
