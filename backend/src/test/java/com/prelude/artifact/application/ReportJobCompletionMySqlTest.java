package com.prelude.artifact.application;

import com.prelude.jobs.BackgroundJobRecoveryService;
import com.prelude.jobs.integration.BackgroundJobOperations;
import com.prelude.test.AccountFixtures;
import com.prelude.test.ArtifactFixtures;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@EnabledIfEnvironmentVariable(named = "PRELUDE_MYSQL_SMOKE", matches = "true")
@SpringBootTest(properties = "spring.rabbitmq.listener.simple.auto-startup=false")
class ReportJobCompletionMySqlTest {

    @Autowired
    private ReportJobCompletion completion;

    @Autowired
    private BackgroundJobOperations jobs;

    @Autowired
    private BackgroundJobRecoveryService recoveryService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void staleAttemptCannotCommitReportStateAfterReplacementClaim() {
        Fixture fixture = createFixture();
        var ref = request(fixture.accountId(), fixture.sessionId());
        int attemptOne = jobs.claim(ref.jobId()).attemptNumber();
        expireLease(ref.jobId());
        assertThat(recoveryService.recover(ref.jobId(), LocalDateTime.now()))
            .isEqualTo(BackgroundJobRecoveryService.RecoveryOutcome.RETRY_SCHEDULED);
        int attemptTwo = jobs.claim(ref.jobId()).attemptNumber();

        assertThat(completion.complete(
            ref.jobId(), attemptOne, fixture.sessionId(), generated(fixture))).isFalse();

        JobState replacement = storedJob(ref.jobId());
        assertThat(sessionStatus(fixture.sessionId())).isEqualTo("generating");
        assertThat(sessionSummaryReport(fixture.sessionId())).isNull();
        assertThat(replacement.status()).isEqualTo("RUNNING");
        assertThat(replacement.attemptCount()).isEqualTo(attemptTwo);
        assertThat(scoreRows(fixture.sessionId())).isZero();
        assertThat(weaknessRows(fixture.sessionId())).isZero();

        assertThat(completion.complete(
            ref.jobId(), attemptTwo, fixture.sessionId(), generated(fixture))).isTrue();

        JobState succeeded = storedJob(ref.jobId());
        assertThat(sessionStatus(fixture.sessionId())).isEqualTo("finished");
        assertThat(sessionSummaryReport(fixture.sessionId())).isEqualTo("{\"report\":\"ready\"}");
        assertThat(succeeded.status()).isEqualTo("SUCCEEDED");
        assertThat(scoreRows(fixture.sessionId())).isEqualTo(1);
        assertThat(weaknessRows(fixture.sessionId())).isEqualTo(1);
    }

    @Test
    void failedDomainFinalizationRollsBackTheJobSuccessTransition() {
        Fixture fixture = createFixture();
        var ref = request(fixture.accountId(), fixture.sessionId());
        int attemptNumber = jobs.claim(ref.jobId()).attemptNumber();

        updateSessionStatus(fixture.sessionId(), "ongoing");

        assertThatThrownBy(() -> completion.complete(
            ref.jobId(), attemptNumber, fixture.sessionId(), generated(fixture)))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("lost generating state");

        JobState job = storedJob(ref.jobId());
        assertThat(job.status()).isEqualTo("RUNNING");
        assertThat(job.attemptCount()).isEqualTo(attemptNumber);
        assertThat(sessionStatus(fixture.sessionId())).isEqualTo("ongoing");
        assertThat(sessionSummaryReport(fixture.sessionId())).isNull();
        assertThat(scoreRows(fixture.sessionId())).isZero();
        assertThat(weaknessRows(fixture.sessionId())).isZero();
    }

    private Fixture createFixture() {
        long nano = System.nanoTime();
        long accountId = AccountFixtures.create(jdbcTemplate, "report-atomic");

        long resumeId = insert(
            "INSERT INTO resume (account_id, file_name, raw_text, parsed_skills, parsed_projects) VALUES (?, ?, ?, ?, ?)",
            accountId, "resume-" + nano + ".pdf", "resume", "[]", "[]");

        long positionId = insert(
            "INSERT INTO position_template (account_id, name, system_prompt) VALUES (?, ?, ?)",
            accountId, "position-" + nano, "system");

        long profileId = insert(
            "INSERT INTO model_profile (account_id, provider, model, reasoning_level, effective_parameters_json, fallback_capabilities_json) VALUES (?, ?, ?, ?, ?, ?)",
            accountId, "deepseek", "deepseek-v4-pro", "AUTO", "{\"maxOutputTokens\":4096}", "[]");

        long snapshotId = insert("""
            INSERT INTO model_execution_snapshot (account_id, profile_id, provider, model, reasoning_level, effective_parameters_json, capability_version, model_capability_json, fallback_capabilities_json)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            accountId, profileId, "deepseek", "deepseek-v4-pro", "AUTO", "{\"maxOutputTokens\":4096}", "2026-08-30",
            """
            {"provider":"deepseek","model":"deepseek-v4-pro","reasoning":true,
             "structuredOutput":true,"toolCalling":true,"streaming":true,"vision":false,
             "multilingual":true,"longContext":true,"embedding":false,"nativeRealtimeVoice":false,
             "supportedReasoningLevels":["AUTO","LOW","HIGH","MAX"]}
            """,
            "[]");

        long sessionId = insert(
            "INSERT INTO interview_session (account_id, resume_id, position_id, target_position, model_execution_snapshot_id, status) VALUES (?, ?, ?, ?, ?, ?)",
            accountId, resumeId, positionId, "position-" + nano, snapshotId, "generating");

        return new Fixture(accountId, sessionId);
    }

    private long insert(String sql, Object... params) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(con -> {
            PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            for (int i = 0; i < params.length; i++) {
                ps.setObject(i + 1, params[i]);
            }
            return ps;
        }, keyHolder);
        Number key = keyHolder.getKey();
        return key == null ? 0L : key.longValue();
    }

    private String sessionStatus(long sessionId) {
        return jdbcTemplate.queryForObject(
            "SELECT status FROM interview_session WHERE id = ?", String.class, sessionId);
    }

    private String sessionSummaryReport(long sessionId) {
        return jdbcTemplate.queryForObject(
            "SELECT summary_report FROM interview_session WHERE id = ?", String.class, sessionId);
    }

    private void updateSessionStatus(long sessionId, String status) {
        jdbcTemplate.update(
            "UPDATE interview_session SET status = ? WHERE id = ?", status, sessionId);
    }

    private BackgroundJobOperations.BackgroundJobRef request(long accountId, long sessionId) {
        return jobs.request(new BackgroundJobOperations.BackgroundJobRequest(
            "report.generate",
            accountId,
            sessionId,
            "report.generate:atomic:" + System.nanoTime(),
            "{}"
        ));
    }

    private GenerateInterviewReport.GenerationResult generated(Fixture fixture) {
        return ArtifactFixtures.generationResult(fixture.accountId(), fixture.sessionId());
    }

    private void expireLease(String jobId) {
        jdbcTemplate.update(
            "UPDATE background_job SET lease_expires_at = ? WHERE job_id = ?",
            LocalDateTime.now().minusSeconds(1), jobId);
    }

    private JobState storedJob(String jobId) {
        return jdbcTemplate.queryForObject(
            "SELECT status, attempt_count FROM background_job WHERE job_id = ?",
            (rs, rowNum) -> new JobState(rs.getString("status"), rs.getInt("attempt_count")),
            jobId);
    }

    private record JobState(String status, int attemptCount) {
    }

    private long scoreRows(long sessionId) {
        Long count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM score_history WHERE session_id = ?", Long.class, sessionId);
        return count == null ? 0 : count;
    }

    private long weaknessRows(long sessionId) {
        Long count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM account_weakness WHERE session_id = ?", Long.class, sessionId);
        return count == null ? 0 : count;
    }

    private record Fixture(long accountId, long sessionId) {
    }
}
