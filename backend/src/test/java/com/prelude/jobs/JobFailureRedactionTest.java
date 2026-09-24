package com.prelude.jobs;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JobFailureRedactionTest {

    @Test
    void redactsBearerNamedAndApiKeySecrets() {
        assertThat(JobFailureRedaction.sanitize(
            new RuntimeException("call failed Authorization: Bearer sk-abc123def456ghi")))
            .doesNotContain("sk-abc123def456ghi")
            .contains("[REDACTED]");
        assertThat(JobFailureRedaction.sanitize(
            new RuntimeException("api_key=super-secret-value")))
            .doesNotContain("super-secret-value");
    }

    @Test
    void stripsControlCharactersAndTruncates() {
        String sanitized = JobFailureRedaction.sanitize(
            new RuntimeException("line1\r\nline2\t" + "x".repeat(2000)));
        assertThat(sanitized).doesNotContain("\r").doesNotContain("\n");
        assertThat(sanitized.length()).isLessThanOrEqualTo(1024);
    }
}
