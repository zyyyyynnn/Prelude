package com.interview.resume.domain;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ResumeDocumentProjectorTest {

    private final ResumeDocumentProjector projector = new ResumeDocumentProjector();

    @Test
    void rendersCanonicalDocumentInDeterministicSectionOrder() {
        ResumeDocument document = new ResumeDocument(
            1,
            "zh-CN",
            new ResumeDocument.Profile("张三", "zhang@example.com", "13800000000", "Java 工程师"),
            "8 年后端研发经验",
            List.of(new ResumeDocument.Skill("Java", "expert")),
            List.of(new ResumeDocument.Experience(
                "Prelude", "高级工程师", "2023-01", "至今", List.of("负责核心架构")
            )),
            List.of(new ResumeDocument.Project(
                "智能面试系统", "负责人", List.of("Java", "Vue"), List.of("落地模块化单体"), "交付稳定版本"
            )),
            List.of(new ResumeDocument.Education("示例大学", "计算机硕士", "2018")),
            List.of("开源贡献者")
        );

        ResumeDocumentProjection first = projector.project(document);
        ResumeDocumentProjection second = projector.project(document);

        assertThat(first).isEqualTo(second);
        assertThat(first.skills()).containsExactly("Java");
        assertThat(first.projectsSummary()).containsExactly("智能面试系统：落地模块化单体；交付稳定版本");
        assertThat(first.plainText()).isEqualTo("""
            基本信息
            姓名：张三
            邮箱：zhang@example.com
            电话：13800000000
            目标岗位：Java 工程师

            职业概述
            8 年后端研发经验

            技能
            Java（expert）

            工作经历
            Prelude · 高级工程师 · 2023-01 - 至今
            - 负责核心架构

            项目经历
            智能面试系统 · 负责人
            技术栈：Java、Vue
            - 落地模块化单体
            成果：交付稳定版本

            教育经历
            示例大学 · 计算机硕士 · 2018

            其他
            开源贡献者""");
    }

    @Test
    void omitsBlankFieldsAndSections() {
        ResumeDocument document = new ResumeDocument(
            1, "zh-CN", null, "", List.of(), List.of(), List.of(), List.of(), List.of("原始文本")
        );

        ResumeDocumentProjection projection = projector.project(document);

        assertThat(projection.plainText()).isEqualTo("其他\n原始文本");
        assertThat(projection.skills()).isEmpty();
        assertThat(projection.projectsSummary()).isEmpty();
    }
}
