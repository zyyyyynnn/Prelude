package com.interview.resume.domain;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ResumeDocumentFactoryTest {

    @Test
    void preservesImportedTextAndMapsParsedFields() {
        ResumeDocument document = ResumeDocumentFactory.fromImport(
            "完整 PDF 原文",
            List.of("Java", "Spring"),
            List.of(new ResumeDocumentFactory.ImportedProject("Prelude", "面试系统"))
        );

        assertThat(document.schemaVersion()).isEqualTo(1);
        assertThat(document.locale()).isEqualTo("zh-CN");
        assertThat(document.skills()).extracting(ResumeDocument.Skill::name)
            .containsExactly("Java", "Spring");
        assertThat(document.projects()).singleElement().satisfies(project -> {
            assertThat(project.name()).isEqualTo("Prelude");
            assertThat(project.bullets()).containsExactly("面试系统");
        });
        assertThat(document.extras()).containsExactly("完整 PDF 原文");
    }
}
