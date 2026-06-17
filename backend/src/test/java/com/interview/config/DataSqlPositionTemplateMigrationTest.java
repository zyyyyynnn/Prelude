package com.interview.config;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

class DataSqlPositionTemplateMigrationTest {

    private static final String JAVA_BAD_NAME_HEX = "0x4A61766120C3A5C290C28EC3A7C2ABC2AFC3A5C2B7C2A5C3A7C2A8C28BC3A5C2B8C288";
    private static final String JAVA_CURRENT_BAD_NAME_HEX = "0x4A61766120C3A5C290C5BDC3A7C2ABC2AFC3A5C2B7C2A5C3A7C2A8E280B9C3A5C2B8CB86";
    private static final String FRONTEND_BAD_NAME_HEX = "0xC3A5C289C28DC3A7C2ABC2AFC3A5C2B7C2A5C3A7C2A8C28BC3A5C2B8C288";
    private static final String FRONTEND_CURRENT_BAD_NAME_HEX = "0xC3A5E280B0C28DC3A7C2ABC2AFC3A5C2B7C2A5C3A7C2A8E280B9C3A5C2B8CB86";
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

        assertThat(countOccurrences(sql, "JOIN `position_template` bad_position ON s.`position_id` = bad_position.`id`")).isEqualTo(3);
        assertThat(countOccurrences(sql, "SET s.`position_id` = default_position.`id`")).isEqualTo(3);
        assertThat(countOccurrences(sql, "s.`target_position` = default_position.`name`")).isEqualTo(3);
        assertThat(countOccurrences(sql, "DELETE bad_position")).isEqualTo(3);
        assertThat(countOccurrences(sql, "ORDER BY `id`")).isEqualTo(3);
        assertThat(countOccurrences(sql, "LIMIT 1")).isGreaterThanOrEqualTo(3);
        assertThat(countOccurrences(sql, "WHERE bad_position.`name` IN")).isEqualTo(6);
        assertThat(sql)
            .contains(JAVA_CURRENT_BAD_NAME_HEX)
            .contains(FRONTEND_CURRENT_BAD_NAME_HEX);
    }

    @Test
    void demoSeedIsScopedToDemoUserAndDefaultSessions() throws Exception {
        String sql = readDataSql();

        assertThat(sql).contains(
            "-- === BEGIN DEMO SEED DATA ===",
            "-- 1. DELETE EXISTING DEMO SESSIONS IN THIS TIME RANGE",
            "WHERE u.`username` = 'demo'",
            "INSERT INTO `interview_session`",
            "INSERT INTO `interview_message`",
            "INSERT INTO `score_history`",
            "INSERT INTO `user_weakness`"
        );
        assertThat(sql)
            .doesNotContain("DELETE s\nFROM `interview_session` s")
            .doesNotContain("DELETE FROM `interview_session`;");
        assertThat(countOccurrences(sql, "INSERT INTO `interview_session`")).isEqualTo(7);
        assertThat(sql)
            .contains("JOIN `interview_session` s ON uw.`session_id` = s.`id`")
            .contains("JOIN `interview_session` s ON sh.`session_id` = s.`id`")
            .contains("JOIN `interview_session` s ON im.`session_id` = s.`id`")
            .contains("Spring 事务")
            .contains("组件 API 设计")
            .contains("简历与岗位匹配")
            .contains("请求的幂等控制")
            .contains("暗色主题");
        assertThat(sql)
            .doesNotContain("DELETE FROM `user`")
            .doesNotContain("DELETE FROM `resume`");
    }

    @Test
    void demoSeedMigratesOldAprilDataToJune() throws Exception {
        String sql = readDataSql();

        // data.sql 包含旧 4 月 demo natural key 及其迁移逻辑
        assertThat(sql).containsSubsequence(
            "-- 3.0 清理或迁移旧的 4 月 demo session 到 6 月",
            "DELETE uw",
            "FROM `user_weakness` uw",
            "JOIN `interview_session` old_s ON uw.`session_id` = old_s.`id`",
            "JOIN `user` u ON old_s.`user_id` = u.`id` AND u.`username` = 'demo'",
            "WHERE old_s.`created_at` IN ('2026-04-23 14:00:00', '2026-04-22 10:00:00', '2026-04-20 16:10:00', '2026-04-18 15:30:00')",
            "DELETE old_s",
            "FROM `interview_session` old_s",
            "JOIN `user` u ON old_s.`user_id` = u.`id` AND u.`username` = 'demo'",
            "UPDATE `interview_session` old_s",
            "JOIN `user` u ON old_s.`user_id` = u.`id` AND u.`username` = 'demo'",
            "WHEN '2026-04-23 14:00:00' THEN '2026-06-16 14:00:00'"
        );

        // 确保清理逻辑严格作用于 demo 用户
        assertThat(sql).doesNotContain("DELETE FROM `interview_session`;");
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
        return Files.readString(Path.of(resource.toURI()), StandardCharsets.UTF_8).replace("\r\n", "\n");
    }

    private static int countOccurrences(String value, String needle) {
        int count = 0;
        int index = 0;
        while ((index = value.indexOf(needle, index)) >= 0) {
            count++;
            index += needle.length();
        }
        return count;
    }
}
