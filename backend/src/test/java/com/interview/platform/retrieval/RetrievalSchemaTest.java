package com.interview.platform.retrieval;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

class RetrievalSchemaTest {

    @Test
    void schemaProvidesDurableRebuildSource() throws Exception {
        var resource = Objects.requireNonNull(
            RetrievalSchemaTest.class.getClassLoader().getResource("schema.sql")
        );
        String schema = Files.readString(Path.of(resource.toURI()), StandardCharsets.UTF_8);

        assertThat(schema)
            .contains("CREATE TABLE IF NOT EXISTS `retrieval_chunk`")
            .contains("`scope_type` VARCHAR(32) NOT NULL")
            .contains("`scope_id` BIGINT NOT NULL")
            .contains("`content_hash` CHAR(64) NOT NULL")
            .contains("`embedding_model` VARCHAR(128)")
            .contains("`embedding_dimensions` INT")
            .contains("`embedding_json` LONGTEXT")
            .contains("UNIQUE KEY `uk_retrieval_chunk_scope_ordinal`");
    }
}
