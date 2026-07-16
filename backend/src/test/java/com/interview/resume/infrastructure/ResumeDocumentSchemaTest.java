package com.interview.resume.infrastructure;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class ResumeDocumentSchemaTest {

    @Test
    void canonicalSchemaContainsResumeDocumentAndImprovementStructures() throws Exception {
        try (var input = getClass().getResourceAsStream("/schema.sql")) {
            assertThat(input).isNotNull();
            String schema = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            assertThat(schema)
                .contains("`document_json` LONGTEXT")
                .contains("`document_version` INT")
                .contains("`source_type` VARCHAR(32)")
                .contains("`plain_text_projection` MEDIUMTEXT")
                .contains("COLUMN_NAME = 'document_json'")
                .contains("COLUMN_NAME = 'document_version'")
                .contains("COLUMN_NAME = 'source_type'")
                .contains("COLUMN_NAME = 'plain_text_projection'")
                .contains("CREATE TABLE IF NOT EXISTS `resume_improvement`")
                .contains("`target_path` VARCHAR(128) NOT NULL")
                .contains("`base_document_version` INT NOT NULL")
                .contains("`status` ENUM('pending','accepted','rejected')")
                .contains("UNIQUE KEY `uk_resume_improvement_session_ordinal`");
        }
    }
}
