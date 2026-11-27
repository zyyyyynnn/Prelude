package com.prelude.interview.infrastructure;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import java.sql.Connection;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class InterviewStageBoundaryMigrationTest {

    @Test
    void migrationUsesTheLatestMessageBeforeTheStageStarted() throws Exception {
        DriverManagerDataSource dataSource = dataSource();
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("CREATE TABLE interview_stage (id BIGINT AUTO_INCREMENT PRIMARY KEY, session_id BIGINT,"
            + " stage_name VARCHAR(32), started_at TIMESTAMP, ended_at TIMESTAMP)");
        jdbc.execute("CREATE TABLE interview_message (id BIGINT AUTO_INCREMENT PRIMARY KEY, session_id BIGINT,"
            + " role VARCHAR(16), seq_num INT, created_at TIMESTAMP)");
        jdbc.update("INSERT INTO interview_stage (session_id, stage_name, started_at) VALUES (1, 'technical', ?)",
            LocalDateTime.of(2026, 11, 27, 10, 0));
        jdbc.update("INSERT INTO interview_message (session_id, role, seq_num, created_at) VALUES"
            + " (1, 'system', 0, ?), (1, 'assistant', 1, ?), (1, 'user', 2, ?), (1, 'system', 3, ?)",
            LocalDateTime.of(2026, 11, 27, 9, 0),
            LocalDateTime.of(2026, 11, 27, 9, 5),
            LocalDateTime.of(2026, 11, 27, 9, 6),
            LocalDateTime.of(2026, 11, 27, 10, 0, 1));

        try (Connection connection = dataSource.getConnection()) {
            ScriptUtils.executeSqlScript(connection,
                new ClassPathResource("db/migration/V20261127__make_interview_stage_boundaries_explicit.sql"));
        }

        assertThat(jdbc.queryForObject("SELECT boundary_seq FROM interview_stage WHERE id = 1",
            Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT awaiting_answer FROM interview_stage WHERE id = 1",
            Boolean.class)).isFalse();
    }

    private DriverManagerDataSource dataSource() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.h2.Driver");
        dataSource.setUrl("jdbc:h2:mem:stage-boundary-migration;MODE=MySQL;DB_CLOSE_DELAY=-1");
        dataSource.setUsername("sa");
        return dataSource;
    }
}
