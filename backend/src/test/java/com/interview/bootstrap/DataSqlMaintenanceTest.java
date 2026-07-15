package com.interview.bootstrap;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

class DataSqlMaintenanceTest {

    private static final String JAVA_BAD_NAME_HEX = "0x4A61766120C3A5C290C28EC3A7C2ABC2AFC3A5C2B7C2A5C3A7C2A8C28BC3A5C2B8C288";
    private static final String JAVA_CURRENT_BAD_NAME_HEX = "0x4A61766120C3A5C290C5BDC3A7C2ABC2AFC3A5C2B7C2A5C3A7C2A8E280B9C3A5C2B8CB86";
    private static final String FRONTEND_BAD_NAME_HEX = "0xC3A5C289C28DC3A7C2ABC2AFC3A5C2B7C2A5C3A7C2A8C28BC3A5C2B8C288";
    private static final String FRONTEND_CURRENT_BAD_NAME_HEX = "0xC3A5E280B0C28DC3A7C2ABC2AFC3A5C2B7C2A5C3A7C2A8E280B9C3A5C2B8CB86";
    private static final String ALGORITHM_BAD_NAME_HEX = "0xC3A7C2AEC297C3A6C2B3C295C3A5C2B7C2A5C3A7C2A8C28BC3A5C2B8C288";

    @Test
    void positionTemplateCleanupDoesNotDependOnFixedIds() throws Exception {
        String sql = readDataSql();

        assertThat(sql)
            .doesNotContain("WHERE `id` = 1")
            .doesNotContain("WHERE `id` = 2")
            .doesNotContain("WHERE `id` = 3");
        assertDefaultPositionCleanup(sql, "Java 后端工程师", JAVA_BAD_NAME_HEX);
        assertDefaultPositionCleanup(sql, "前端工程师", FRONTEND_BAD_NAME_HEX);
        assertDefaultPositionCleanup(sql, "算法工程师", ALGORITHM_BAD_NAME_HEX);
        assertThat(sql)
            .contains(JAVA_CURRENT_BAD_NAME_HEX)
            .contains(FRONTEND_CURRENT_BAD_NAME_HEX);
    }

    @Test
    void canonicalDataScriptContainsNoDevelopmentFixtures() throws Exception {
        String sql = readDataSql();

        assertThat(sql.split("\n").length).isGreaterThan(100);
        assertThat(sql)
            .doesNotContain("INSERT INTO `user`")
            .doesNotContain("DELETE uw FROM")
            .doesNotContain("DELETE sh FROM")
            .doesNotContain("'demo'")
            .doesNotContain("Session S")
            .doesNotContain("2026-06-");
    }

    @Test
    void legacyProviderValuesConvergeWithoutRewritingEncryptedKeys() throws Exception {
        String sql = readDataSql();

        assertThat(sql)
            .contains("WHERE `llm_provider` IN ('openai', 'openai-compatible')")
            .contains("`llm_provider` = 'openai-chat-completions'")
            .contains("WHERE `llm_provider` = 'anthropic'")
            .contains("`llm_provider` = 'anthropic-messages'")
            .contains("'openai-responses'", "'openai-chat-completions'", "'anthropic-messages'")
            .doesNotContain("SET `llm_api_key_encrypted`")
            .doesNotContain("`llm_api_key_encrypted` =");
    }

    private static void assertDefaultPositionCleanup(String sql, String defaultName, String badNameHex) {
        assertThat(sql).containsSubsequence(
            "UPDATE `position_template`",
            "WHERE `name` IN",
            badNameHex,
            "AND NOT EXISTS",
            "WHERE `name` = '" + defaultName + "'",
            "ORDER BY `id`",
            "LIMIT 1",
            "INSERT INTO `position_template`",
            "SELECT '" + defaultName + "'",
            "WHERE NOT EXISTS",
            "UPDATE `interview_session` s",
            "SET s.`position_id` = default_position.`id`",
            "DELETE bad_position"
        );
    }

    private static String readDataSql() throws IOException, URISyntaxException {
        var resource = Objects.requireNonNull(
            DataSqlMaintenanceTest.class.getClassLoader().getResource("data.sql")
        );
        return Files.readString(Path.of(resource.toURI()), StandardCharsets.UTF_8).replace("\r\n", "\n");
    }
}
