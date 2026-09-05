package com.prelude;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.prelude.jobs.integration.BackgroundJobOperations;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;

import javax.sql.DataSource;
import java.sql.Connection;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@EnabledIfEnvironmentVariable(named = "PRELUDE_MYSQL_SMOKE", matches = "true")
@SpringBootTest(properties = {
    "prelude.jobs.scheduling-enabled=false",
    "prelude.jobs.report.consumer-enabled=false",
    "spring.sql.init.mode=never"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class DevAcceptanceDataResetMySqlTest {

    private static final String DEMO_PASSWORD = "123456";
    private static final String CAPABILITY_VERSION = "2026-09-03";

    @Autowired
    private DataSource dataSource;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private BackgroundJobOperations jobs;

    private Long preservedAccountId;
    private Long trackedDemoAssetId;

    @AfterEach
    void removePreservedProbe() {
        if (trackedDemoAssetId != null) {
            jdbcTemplate.update("DELETE FROM asset WHERE id = ?", trackedDemoAssetId);
        }
        if (preservedAccountId == null) {
            return;
        }
        jdbcTemplate.update("DELETE FROM position_template WHERE account_id = ?", preservedAccountId);
        jdbcTemplate.update("DELETE FROM asset WHERE account_id = ?", preservedAccountId);
        jdbcTemplate.update("DELETE FROM user_account WHERE id = ?", preservedAccountId);
    }

    @Test
    void resetRestoresTheCanonicalDemoDatasetWithoutTouchingOtherAccounts() throws Exception {
        executeDataScript();
        assertCanonicalDataset();

        long demoAccountId = requiredLong(
            "SELECT id FROM user_account WHERE username = 'demo'"
        );
        String suffix = UUID.randomUUID().toString();
        preservedAccountId = insertAndReturnId(
            "INSERT INTO user_account (username, password_hash, email, theme_preference, revision) VALUES (?, ?, ?, 'dark', 4)",
            "reset-probe-" + suffix,
            "probe-hash",
            "reset-probe-" + suffix + "@example.test"
        );
        jdbcTemplate.update(
            "INSERT INTO position_template (account_id, name, system_prompt) VALUES (?, ?, 'preserve me')",
            preservedAccountId,
            "reset-probe-position-" + suffix
        );
        long preservedAssetId = insertAndReturnId(
            "INSERT INTO asset (account_id, kind, object_key, media_type, byte_size, status) VALUES (?, 'attachment', ?, 'text/plain', 12, 'READY')",
            preservedAccountId,
            "reset-probe/" + suffix
        );

        jdbcTemplate.update(
            "UPDATE user_account SET password_hash = 'changed', email = 'changed@example.test', avatar_url = '/api/assets/changed', theme_preference = 'dark', revision = 9, last_operation_id = 'changed-op' WHERE id = ?",
            demoAccountId
        );
        jdbcTemplate.update(
            "INSERT INTO oauth_binding (account_id, provider, provider_subject) VALUES (?, 'github', ?)",
            demoAccountId,
            "reset-probe-" + suffix
        );
        jdbcTemplate.update(
            "INSERT INTO position_template (account_id, name, system_prompt) VALUES (?, ?, 'remove me')",
            demoAccountId,
            "demo-reset-position-" + suffix
        );
        trackedDemoAssetId = insertAndReturnId(
            "INSERT INTO asset (account_id, kind, object_key, media_type, byte_size, status) VALUES (?, 'attachment', ?, 'application/pdf', 24, 'READY')",
            demoAccountId,
            "demo-reset/" + suffix
        );
        jdbcTemplate.update(
            "INSERT INTO attachment (account_id, asset_id, file_name, extracted_text) VALUES (?, ?, 'temporary.pdf', 'remove me')",
            demoAccountId,
            trackedDemoAssetId
        );
        long artifactId = insertAndReturnId(
            "INSERT INTO artifact (account_id, kind) VALUES (?, ?)",
            demoAccountId,
            "reset-probe-" + suffix
        );
        jdbcTemplate.update(
            "INSERT INTO artifact_version (artifact_id, version_number, asset_id, provenance_json) VALUES (?, 1, ?, '{}')",
            artifactId,
            trackedDemoAssetId
        );
        long credentialId = insertAndReturnId(
            "INSERT INTO provider_credential (account_id, provider, scope_key, api_key_encrypted) VALUES (?, 'deepseek', ?, 'remove-me')",
            demoAccountId,
            "reset-probe-" + suffix
        );
        jdbcTemplate.update(
            "UPDATE model_profile SET credential_id = ?, custom_endpoint_url = 'https://example.test/v1', reasoning_level = 'MAX' WHERE account_id = ?",
            credentialId,
            demoAccountId
        );

        long oldResumeId = insertAndReturnId(
            "INSERT INTO resume (account_id, file_name, raw_text, parsed_skills, parsed_projects) VALUES (?, 'temporary.pdf', 'remove me', '[]', '[]')",
            demoAccountId
        );
        long oldProfileId = requiredLong(
            "SELECT id FROM model_profile WHERE account_id = ?",
            demoAccountId
        );
        long oldSnapshotId = insertAndReturnId(
            "INSERT INTO model_execution_snapshot (account_id, profile_id, provider, model, reasoning_level, effective_parameters_json, capability_version, model_capability_json, fallback_capabilities_json, credential_id, custom_endpoint_url) VALUES (?, ?, 'deepseek', 'deepseek-v4-pro', 'MAX', '{}', ?, '{}', '[]', ?, 'https://example.test/v1')",
            demoAccountId,
            oldProfileId,
            CAPABILITY_VERSION,
            credentialId
        );
        long oldSessionId = insertAndReturnId(
            "INSERT INTO interview_session (account_id, resume_id, position_id, target_position, model_execution_snapshot_id, status) VALUES (?, ?, (SELECT id FROM position_template WHERE account_id IS NULL ORDER BY id LIMIT 1), 'temporary', ?, 'ongoing')",
            demoAccountId,
            oldResumeId,
            oldSnapshotId
        );
        jdbcTemplate.update(
            "INSERT INTO retrieval_chunk (scope_type, scope_id, ordinal, content, content_hash) VALUES ('resume', ?, 0, 'remove me', REPEAT('a', 64)), ('session', ?, 0, 'remove me', REPEAT('b', 64))",
            oldResumeId,
            oldSessionId
        );

        String oldJobId = UUID.randomUUID().toString();
        jdbcTemplate.update(
            "INSERT INTO background_job (job_id, type, account_id, subject_id, operation_key, payload_json, status, attempt_count) VALUES (?, 'report.generate', ?, ?, ?, '{}', 'RUNNING', 1)",
            oldJobId,
            demoAccountId,
            oldSessionId,
            "reset-probe-" + suffix
        );
        jdbcTemplate.update(
            "INSERT INTO job_attempt (job_id, attempt_number, status, started_at) VALUES (?, 1, 'RUNNING', NOW())",
            oldJobId
        );
        String publicationId = UUID.randomUUID().toString();
        jdbcTemplate.update(
            "INSERT INTO EVENT_PUBLICATION (ID, LISTENER_ID, EVENT_TYPE, SERIALIZED_EVENT, PUBLICATION_DATE, STATUS, COMPLETION_ATTEMPTS) VALUES (?, 'probe', 'probe', ?, NOW(6), 'PUBLISHED', 0)",
            publicationId,
            "{\"jobId\":\"" + oldJobId + "\"}"
        );

        executeDataScript();

        assertCanonicalDataset();
        Map<String, Object> account = jdbcTemplate.queryForMap(
            "SELECT password_hash, email, avatar_url, theme_preference, revision, last_operation_id FROM user_account WHERE id = ?",
            demoAccountId
        );
        assertThat(passwordEncoder.matches(DEMO_PASSWORD, (String) account.get("password_hash"))).isTrue();
        assertThat(account)
            .containsEntry("email", "demo@prelude.local")
            .containsEntry("theme_preference", "system");
        assertThat(account.get("avatar_url")).isNull();
        assertThat(((Number) account.get("revision")).longValue()).isZero();
        assertThat(account.get("last_operation_id")).isNull();

        assertThat(count("SELECT COUNT(*) FROM oauth_binding WHERE account_id = ?", demoAccountId)).isZero();
        assertThat(count("SELECT COUNT(*) FROM position_template WHERE account_id = ?", demoAccountId)).isZero();
        assertThat(count("SELECT COUNT(*) FROM attachment WHERE account_id = ?", demoAccountId)).isZero();
        assertThat(count("SELECT COUNT(*) FROM artifact WHERE account_id = ?", demoAccountId)).isZero();
        assertThat(count("SELECT COUNT(*) FROM provider_credential WHERE account_id = ?", demoAccountId)).isZero();
        assertThat(count("SELECT COUNT(*) FROM retrieval_chunk WHERE (scope_type = 'resume' AND scope_id = ?) OR (scope_type = 'session' AND scope_id = ?)", oldResumeId, oldSessionId)).isZero();
        assertThat(count("SELECT COUNT(*) FROM background_job WHERE job_id = ?", oldJobId)).isZero();
        assertThat(count("SELECT COUNT(*) FROM job_attempt WHERE job_id = ?", oldJobId)).isZero();
        assertThat(count("SELECT COUNT(*) FROM EVENT_PUBLICATION WHERE ID = ?", publicationId)).isZero();
        assertThat(count("SELECT COUNT(*) FROM asset WHERE id = ?", trackedDemoAssetId)).isOne();
        assertThat(count("SELECT COUNT(*) FROM user_account WHERE id = ?", preservedAccountId)).isOne();
        assertThat(count("SELECT COUNT(*) FROM position_template WHERE account_id = ?", preservedAccountId)).isOne();
        assertThat(count("SELECT COUNT(*) FROM asset WHERE id = ?", preservedAssetId)).isOne();
        assertThatThrownBy(() -> jobs.dispatchedJob(oldJobId))
            .isInstanceOfSatisfying(BusinessException.class, exception -> {
                assertThat(exception.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                assertThat(exception.getCode()).isEqualTo("job_not_found");
            });
    }

    private void assertCanonicalDataset() throws Exception {
        long demoAccountId = requiredLong("SELECT id FROM user_account WHERE username = 'demo'");
        assertThat(count("SELECT COUNT(*) FROM user_account WHERE username = 'demo'")).isOne();
        assertThat(count("SELECT COUNT(*) FROM resume WHERE account_id = ?", demoAccountId)).isEqualTo(3);
        assertThat(jdbcTemplate.queryForList(
            "SELECT file_name FROM resume WHERE account_id = ? ORDER BY created_at",
            String.class,
            demoAccountId
        )).containsExactly(
            "Java 后端工程师简历.pdf",
            "前端工程师简历.pdf",
            "算法工程师简历.pdf"
        );
        assertThat(count("SELECT COUNT(*) FROM interview_session WHERE account_id = ? AND status = 'finished'", demoAccountId)).isEqualTo(3);
        assertThat(count("SELECT COUNT(*) FROM interview_session WHERE account_id = ? AND status = 'ongoing'", demoAccountId)).isOne();
        assertThat(count("SELECT COUNT(DISTINCT resume_id) FROM interview_session WHERE account_id = ?", demoAccountId)).isEqualTo(3);
        assertThat(count("SELECT COUNT(*) FROM model_profile WHERE account_id = ? AND provider = 'deepseek' AND model = 'deepseek-v4-pro' AND reasoning_level = 'AUTO' AND credential_id IS NULL AND custom_endpoint_url IS NULL", demoAccountId)).isOne();
        assertThat(count("SELECT COUNT(*) FROM model_execution_snapshot WHERE account_id = ?", demoAccountId)).isEqualTo(4);
        assertThat(count("SELECT COUNT(*) FROM model_execution_snapshot WHERE account_id = ? AND capability_version < ?", demoAccountId, CAPABILITY_VERSION)).isZero();
        assertThat(count("SELECT COUNT(*) FROM interview_session WHERE account_id = ? AND created_at < '2026-09-03 00:00:00'", demoAccountId)).isZero();
        assertThat(count("SELECT COUNT(*) FROM interview_session WHERE account_id = ? AND jd_text IS NOT NULL", demoAccountId)).isGreaterThanOrEqualTo(3);
        assertThat(count("SELECT COUNT(*) FROM interview_session WHERE account_id = ? AND jd_text IS NULL", demoAccountId)).isGreaterThanOrEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM score_history WHERE account_id = ?", demoAccountId)).isEqualTo(3);
        assertThat(requiredLong("SELECT MIN(LEAST(technical_score, expression_score, logic_score)) FROM score_history WHERE account_id = ?", demoAccountId)).isLessThanOrEqualTo(6);
        assertThat(requiredLong("SELECT MAX(GREATEST(technical_score, expression_score, logic_score)) FROM score_history WHERE account_id = ?", demoAccountId)).isGreaterThanOrEqualTo(8);
        assertThat(count("SELECT COUNT(DISTINCT hint) FROM interview_message message INNER JOIN interview_session session ON session.id = message.session_id WHERE session.account_id = ? AND message.role = 'user' AND message.hint IS NOT NULL", demoAccountId)).isGreaterThanOrEqualTo(12);
        assertThat(count("SELECT COUNT(*) FROM resume WHERE account_id = ? AND (raw_text LIKE '%10万%' OR raw_text LIKE '%百亿%' OR raw_text LIKE '%千亿%')", demoAccountId)).isZero();
        assertTrendIsNotMonotonic(demoAccountId);
        assertStageTimelines(demoAccountId);
        assertFinishedReportsMatchSourceAnswers(demoAccountId);
    }

    private void assertTrendIsNotMonotonic(long accountId) {
        List<Integer> scores = jdbcTemplate.queryForList(
            "SELECT technical_score FROM score_history WHERE account_id = ? ORDER BY created_at",
            Integer.class,
            accountId
        );
        boolean rose = false;
        boolean fell = false;
        for (int index = 1; index < scores.size(); index++) {
            rose |= scores.get(index) > scores.get(index - 1);
            fell |= scores.get(index) < scores.get(index - 1);
        }
        assertThat(rose).isTrue();
        assertThat(fell).isTrue();
    }

    private void assertFinishedReportsMatchSourceAnswers(long accountId) throws Exception {
        List<Map<String, Object>> sessions = jdbcTemplate.queryForList(
            "SELECT id, summary_report FROM interview_session WHERE account_id = ? AND status = 'finished' ORDER BY created_at",
            accountId
        );
        for (Map<String, Object> session : sessions) {
            long sessionId = ((Number) session.get("id")).longValue();
            JsonNode report = objectMapper.readTree((String) session.get("summary_report"));
            assertThat(report.path("questionReviews").size()).isEqualTo(4);
            Map<String, AnswerEvidence> answersByQuestion = answersByQuestion(sessionId);
            for (JsonNode review : report.path("questionReviews")) {
                String question = review.path("question").asText();
                String answerSummary = review.path("answerSummary").asText();
                assertThat(answerSummary).doesNotContain("…").doesNotContain("...");
                AnswerEvidence evidence = answersByQuestion.get(question);
                assertThat(evidence).isNotNull();
                assertThat(answerSummary).isEqualTo(evidence.answer());
                assertThat(review.path("score").asInt()).isEqualTo(evidence.score());
                assertThat(review.path("scoringReason").asText()).isEqualTo(evidence.hint());
                assertThat(review.path("improvementSuggestion").asText()).isNotBlank();
            }
            for (Map<String, Object> weakness : jdbcTemplate.queryForList(
                "SELECT category, description FROM account_weakness WHERE session_id = ?",
                sessionId
            )) {
                assertThat(report.path("weaknesses").toString())
                    .contains((String) weakness.get("category"))
                    .contains((String) weakness.get("description"));
            }
        }
    }

    private void assertStageTimelines(long accountId) {
        List<Long> finishedSessionIds = jdbcTemplate.queryForList(
            "SELECT id FROM interview_session WHERE account_id = ? AND status = 'finished'",
            Long.class,
            accountId
        );
        for (Long sessionId : finishedSessionIds) {
            List<StageWindow> stages = stageWindows(sessionId);
            assertThat(stages).extracting(StageWindow::name)
                .containsExactly("warmup", "technical", "deep_dive", "closing");
            for (int index = 0; index < stages.size(); index++) {
                StageWindow stage = stages.get(index);
                assertThat(stage.endedAt()).isNotNull().isAfter(stage.startedAt());
                if (index > 0) {
                    assertThat(stage.startedAt()).isEqualTo(stages.get(index - 1).endedAt());
                }
            }
            List<LocalDateTime> transitions = jdbcTemplate.queryForList(
                "SELECT created_at FROM interview_message WHERE session_id = ? AND role = 'system' AND content LIKE '面试已进入%' ORDER BY seq_num",
                LocalDateTime.class,
                sessionId
            );
            assertThat(transitions).containsExactly(
                stages.get(1).startedAt(),
                stages.get(2).startedAt(),
                stages.get(3).startedAt()
            );
        }
        long ongoingSessionId = requiredLong(
            "SELECT id FROM interview_session WHERE account_id = ? AND status = 'ongoing'",
            accountId
        );
        List<StageWindow> ongoingStages = stageWindows(ongoingSessionId);
        assertThat(ongoingStages).extracting(StageWindow::name)
            .containsExactly("warmup", "technical");
        assertThat(ongoingStages.get(0).endedAt()).isEqualTo(ongoingStages.get(1).startedAt());
        assertThat(ongoingStages.get(1).endedAt()).isNull();
    }

    private List<StageWindow> stageWindows(long sessionId) {
        return jdbcTemplate.query(
            "SELECT stage_name, started_at, ended_at FROM interview_stage WHERE session_id = ? ORDER BY started_at",
            (resultSet, rowNumber) -> new StageWindow(
                resultSet.getString("stage_name"),
                resultSet.getObject("started_at", LocalDateTime.class),
                resultSet.getObject("ended_at", LocalDateTime.class)
            ),
            sessionId
        );
    }

    private Map<String, AnswerEvidence> answersByQuestion(long sessionId) {
        List<Map<String, Object>> messages = jdbcTemplate.queryForList(
            "SELECT role, content, score, hint FROM interview_message WHERE session_id = ? ORDER BY seq_num",
            sessionId
        );
        Map<String, AnswerEvidence> answers = new HashMap<>();
        List<String> pendingQuestions = new ArrayList<>();
        for (Map<String, Object> message : messages) {
            String role = (String) message.get("role");
            String content = (String) message.get("content");
            if ("assistant".equals(role)) {
                pendingQuestions.add(content);
            } else if ("user".equals(role) && !pendingQuestions.isEmpty()) {
                answers.put(
                    pendingQuestions.remove(pendingQuestions.size() - 1),
                    new AnswerEvidence(
                        content,
                        ((Number) message.get("score")).intValue(),
                        (String) message.get("hint")
                    )
                );
            }
        }
        return answers;
    }

    private void executeDataScript() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("data-dev.sql"));
        }
    }

    private long insertAndReturnId(String sql, Object... arguments) {
        jdbcTemplate.update(sql, arguments);
        return requiredLong("SELECT LAST_INSERT_ID()");
    }

    private long count(String sql, Object... arguments) {
        return requiredLong(sql, arguments);
    }

    private long requiredLong(String sql, Object... arguments) {
        Long value = jdbcTemplate.queryForObject(sql, Long.class, arguments);
        assertThat(value).isNotNull();
        return value;
    }

    private record StageWindow(String name, LocalDateTime startedAt, LocalDateTime endedAt) {
    }

    private record AnswerEvidence(String answer, int score, String hint) {
    }
}
