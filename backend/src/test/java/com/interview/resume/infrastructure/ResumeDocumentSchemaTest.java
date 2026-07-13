package com.interview.resume.infrastructure;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class ResumeDocumentSchemaTest {

    @Test
    void schemaContainsNullableResumeDocumentExpansionAndIdempotentAlter() throws Exception {
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
                .contains("COLUMN_NAME = 'plain_text_projection'");
        }
        try (var input = getClass().getResourceAsStream("/migrations/20260712_resume_document_expand.sql")) {
            assertThat(input).isNotNull();
            String migration = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            assertThat(migration)
                .contains("ADD COLUMN `document_json` LONGTEXT NULL")
                .contains("ADD COLUMN `document_version` INT NULL")
                .contains("ADD COLUMN `source_type` VARCHAR(32) NULL")
                .contains("ADD COLUMN `plain_text_projection` MEDIUMTEXT NULL");
        }
    }
}
