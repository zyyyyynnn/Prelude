package com.prelude.interview.infrastructure;

import com.prelude.interview.domain.AssessmentStatus;
import com.prelude.interview.domain.InterviewMessage;
import com.prelude.interview.infrastructure.persistence.InterviewMessageEntity;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import java.sql.Connection;

import static org.assertj.core.api.Assertions.assertThat;

class InterviewAssessmentMigrationTest {

    @Test
    void migrationBackfillsHistoryAndAppliesNewMessageDefaults() throws Exception {
        DriverManagerDataSource dataSource = dataSource();
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("CREATE TABLE interview_message (id BIGINT AUTO_INCREMENT PRIMARY KEY, role VARCHAR(16), score INT)");
        jdbc.update("INSERT INTO interview_message (role, score) VALUES ('user', 8), ('user', NULL), ('assistant', NULL), ('system', NULL)");

        try (Connection connection = dataSource.getConnection()) {
            ScriptUtils.executeSqlScript(connection,
                new ClassPathResource("db/migration/V20261125__add_interview_assessment_status.sql"));
        }

        assertThat(jdbc.queryForList("SELECT assessment_status FROM interview_message ORDER BY id", String.class))
            .containsExactly("SCORED", "UNSCORED", "UNSCORED", "UNSCORED");
        jdbc.update("INSERT INTO interview_message (role) VALUES ('user')");
        jdbc.update("INSERT INTO interview_message (role, assessment_status) VALUES ('assistant', NULL), ('system', NULL)");
        assertThat(jdbc.queryForList("SELECT assessment_status FROM interview_message WHERE id > 4 ORDER BY id", String.class))
            .containsExactly("PENDING", null, null);
    }

    @Test
    void newUserMessagesDefaultToPendingWhileOtherRolesRemainNull() {
        InterviewMessage user = new InterviewMessage();
        user.setRole("user");
        InterviewMessage assistant = new InterviewMessage();
        assistant.setRole("assistant");
        InterviewMessage system = new InterviewMessage();
        system.setRole("system");

        assertThat(InterviewMessageEntity.of(user).getAssessmentStatus()).isEqualTo(AssessmentStatus.PENDING);
        assertThat(InterviewMessageEntity.of(assistant).getAssessmentStatus()).isNull();
        assertThat(InterviewMessageEntity.of(system).getAssessmentStatus()).isNull();
    }

    @Test
    void entityMappingPreservesAssessmentState() {
        InterviewMessage message = new InterviewMessage();
        message.setAssessmentStatus(AssessmentStatus.UNSCORED);
        message.setAssessmentError("invalid judge output");

        InterviewMessage restored = InterviewMessageEntity.of(message).toDomain();

        assertThat(restored.getAssessmentStatus()).isEqualTo(AssessmentStatus.UNSCORED);
        assertThat(restored.getAssessmentError()).isEqualTo("invalid judge output");
    }

    private DriverManagerDataSource dataSource() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.h2.Driver");
        dataSource.setUrl("jdbc:h2:mem:assessment-migration;MODE=MySQL;DB_CLOSE_DELAY=-1");
        dataSource.setUsername("sa");
        return dataSource;
    }
}
