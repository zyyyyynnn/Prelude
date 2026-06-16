package com.interview.config;

import org.junit.jupiter.api.Test;

import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.io.IOException;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

class DataSqlPositionTemplateMigrationTest {

    private static final String JAVA_BAD_NAME_HEX = "0x4A61766120C3A5C290C28EC3A7C2ABC2AFC3A5C2B7C2A5C3A7C2A8C28BC3A5C2B8C288";
    private static final String FRONTEND_BAD_NAME_HEX = "0xC3A5C289C28DC3A7C2ABC2AFC3A5C2B7C2A5C3A7C2A8C28BC3A5C2B8C288";
    private static final String ALGORITHM_BAD_NAME_HEX = "0xC3A7C2AEC297C3A6C2B3C295C3A5C2B7C2A5C3A7C2A8C28BC3A5C2B8C288";

    @Test
    void positionTemplateCleanupDoesNotDependOnFixedAutoIncrementIds() throws Exception {
        String sql = readDataSql();

        assertThat(sql)
            .doesNotContain("WHERE `id` = 1")
            .doesNotContain("WHERE `id` = 2")
            .doesNotContain("WHERE `id` = 3");
    }

    @Test
    void positionTemplateCleanupMigratesSessionReferencesBeforeDeletingKnownBadRows() throws Exception {
        String sql = readDataSql();

        assertDefaultPositionCleanup(sql, "Java 后端工程师", JAVA_BAD_NAME_HEX);
        assertDefaultPositionCleanup(sql, "前端工程师", FRONTEND_BAD_NAME_HEX);
        assertDefaultPositionCleanup(sql, "算法工程师", ALGORITHM_BAD_NAME_HEX);
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
            "JOIN `position_template` bad_position ON s.`position_id` = bad_position.`id`",
            "JOIN `position_template` default_position ON default_position.`name` = '" + defaultName + "'",
            "SET s.`position_id` = default_position.`id`",
            "WHERE bad_position.`name` IN",
            badNameHex,
            "DELETE bad_position",
            "FROM `position_template` bad_position",
            "JOIN `position_template` default_position ON default_position.`name` = '" + defaultName + "'",
            "WHERE bad_position.`name` IN",
            badNameHex
        );
    }

    private static String readDataSql() throws IOException, URISyntaxException {
        var resource = Objects.requireNonNull(
            DataSqlPositionTemplateMigrationTest.class.getClassLoader().getResource("data.sql")
        );
        return Files.readString(Path.of(resource.toURI()), StandardCharsets.UTF_8);
    }
}
