package com.interview.platform.job;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class AsyncJobSchemaTest {

    @Test
    void schemaDefinesQueryableIdempotentJobState() throws IOException {
        String schema;
        try (var input = getClass().getResourceAsStream("/schema.sql")) {
            assertThat(input).isNotNull();
            schema = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }

        assertThat(schema)
            .contains("CREATE TABLE IF NOT EXISTS `async_job`")
            .contains("UNIQUE KEY `uk_async_job_job_id`")
            .contains("UNIQUE KEY `uk_async_job_idempotency_key`")
            .contains("'pending','running','succeeded','failed'")
            .contains("`attempts` INT NOT NULL DEFAULT 0")
            .contains("`dispatched_at` DATETIME DEFAULT NULL");
    }
}
