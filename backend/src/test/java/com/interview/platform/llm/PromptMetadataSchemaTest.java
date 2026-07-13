package com.interview.platform.llm;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

class PromptMetadataSchemaTest {

    @Test
    void sessionSchemaStoresPromptVersionSnapshot() throws Exception {
        var resource = Objects.requireNonNull(
            PromptMetadataSchemaTest.class.getClassLoader().getResource("schema.sql")
        );
        String schema = Files.readString(Path.of(resource.toURI()), StandardCharsets.UTF_8);

        assertThat(schema)
            .contains("`prompt_versions_json` VARCHAR(512)")
            .contains("COLUMN_NAME = 'prompt_versions_json'");
    }
}
