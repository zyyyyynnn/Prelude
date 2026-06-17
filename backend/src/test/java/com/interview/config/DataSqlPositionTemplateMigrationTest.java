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
    void demoSeedIsDeterministicAndReviewable() throws Exception {
        String sql = readDataSql();

        // 1. 文件不是压缩 SQL
        String[] lines = sql.split("\n");
        assertThat(lines.length).isGreaterThan(300);
        for (String line : lines) {
            assertThat(line.length()).isLessThanOrEqualTo(240);
        }

        // 2. 禁止裸说明文字
        assertThat(sql)
            .doesNotContain("默认岗位数据与乱码修复/迁移：")
            .doesNotContain("LLM provider 默认配置。");

        // 3. 禁止旧迁移逻辑
        assertThat(sql)
            .doesNotContain("2026-04")
            .doesNotContain("旧的 4 月")
            .doesNotContain("迁移旧")
            .doesNotContain("UPDATE `interview_session` old_s")
            .doesNotContain("INSERT INTO `interview_stage`");

        // 4. 禁止垃圾内容
        assertThat(sql)
            .doesNotContain("推荐算法工程师")
            .doesNotContain("系统设计")
            .doesNotContain("二面")
            .doesNotContain("进阶")
            .doesNotContain("demo message")
            .doesNotContain("placeholder")
            .doesNotContain("模拟数据")
            .doesNotContain("#77");

        // 5. 确定性 seed
        assertThat(sql).contains(
            "2026-06-11 10:00:00",
            "2026-06-12 11:00:00",
            "2026-06-13 14:00:00",
            "2026-06-14 16:00:00",
            "2026-06-15 15:00:00",
            "2026-06-16 10:00:00",
            "2026-06-16 11:30:00"
        );

        assertThat(countOccurrences(sql, "'finished', NULL, CONCAT(")).isEqualTo(5);
        assertThat(countOccurrences(sql, "'ongoing', NULL, NULL,")).isEqualTo(2);

        // 全量清理 SQL
        assertThat(sql).contains("DELETE uw FROM `user_weakness` uw JOIN `interview_session` s ON uw.`session_id` = s.`id` JOIN `user` u ON s.`user_id` = u.`id` WHERE u.`username` = 'demo';");
        
        assertThat(sql).doesNotContain("INSERT INTO `interview_stage`");
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
