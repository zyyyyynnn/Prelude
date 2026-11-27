package com.prelude.interview.application;

import com.prelude.BusinessException;
import com.prelude.context.RetrievalChunkStore;
import com.prelude.context.RetrievalPort;
import com.prelude.interview.application.repository.InterviewSessionRepository;
import com.prelude.interview.domain.InterviewSession;
import com.prelude.jobs.integration.JobTypes;
import com.prelude.test.AccountFixtures;
import com.prelude.test.InterviewDataFixtures;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Proves the session lifecycle guarantees that only a real database can: the
 * cascading removal of every dependent row, and that pinned ordering is decided
 * by the query rather than by the caller.
 */
@EnabledIfEnvironmentVariable(named = "PRELUDE_MYSQL_SMOKE", matches = "true")
@SpringBootTest(properties = "spring.rabbitmq.listener.simple.auto-startup=false")
class InterviewSessionLifecycleMySqlTest {

    @Autowired
    private DeleteInterviewSession deleteInterviewSession;

    @Autowired
    private FinishInterview finishInterview;

    @Autowired
    private PinInterviewSession pinInterviewSession;

    @Autowired
    private InterviewSessionRepository interviewSessionRepository;

    @Autowired
    private InterviewMessageService interviewMessageService;

    @Autowired
    private InterviewTurnCompletion interviewTurnCompletion;

    @Autowired
    private RetrievalChunkStore retrievalChunkStore;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void deletingASessionRemovesEveryDependentRowAndItsRetrievalScope() {
        long accountId = AccountFixtures.create(jdbcTemplate, "delete-cascade");
        long sessionId = createSession(accountId);
        insert(
            "INSERT INTO interview_message (session_id, role, content, seq_num) VALUES (?, 'user', ?, 1)",
            sessionId, "answer");
        insert("INSERT INTO interview_stage (session_id, stage_name, boundary_seq) VALUES (?, 'warmup', -1)", sessionId);
        insert(
            "INSERT INTO score_history (account_id, session_id, technical_score, expression_score, logic_score)"
                + " VALUES (?, ?, 6, 7, 8)",
            accountId, sessionId);
        insert(
            "INSERT INTO account_weakness (account_id, session_id, category, description)"
                + " VALUES (?, ?, '容量估算', '缺少量化')",
            accountId, sessionId);
        retrievalChunkStore.replace(RetrievalPort.SCOPE_SESSION, sessionId, List.of(
            new RetrievalChunkStore.StoredChunk(0, "chunk", "hash-" + System.nanoTime(), null, null)
        ));
        assertThat(count("SELECT COUNT(*) FROM retrieval_chunk WHERE scope_type = 'session' AND scope_id = ?",
            sessionId)).isEqualTo(1);

        AccountFixtures.authenticate(accountId);
        deleteInterviewSession.execute(sessionId);

        assertThat(count("SELECT COUNT(*) FROM interview_session WHERE id = ?", sessionId)).isZero();
        assertThat(count("SELECT COUNT(*) FROM interview_message WHERE session_id = ?", sessionId)).isZero();
        assertThat(count("SELECT COUNT(*) FROM interview_stage WHERE session_id = ?", sessionId)).isZero();
        assertThat(count("SELECT COUNT(*) FROM score_history WHERE session_id = ?", sessionId)).isZero();
        assertThat(count("SELECT COUNT(*) FROM account_weakness WHERE session_id = ?", sessionId)).isZero();
        assertThat(count("SELECT COUNT(*) FROM retrieval_chunk WHERE scope_type = 'session' AND scope_id = ?",
            sessionId)).isZero();
    }

    @Test
    void pinningWinsOverRecencyAndUnpinningClearsTheTimestamp() {
        long accountId = AccountFixtures.create(jdbcTemplate, "pin-order");
        LocalDateTime now = LocalDateTime.now();
        long older = createSession(accountId, now.minusMinutes(10));
        long newer = createSession(accountId, now);
        AccountFixtures.authenticate(accountId);

        assertThat(sessionIds(accountId)).containsExactly(newer, older);

        pinInterviewSession.execute(older, true);
        assertThat(pinned(older)).isTrue();
        assertThat(sessionIds(accountId)).containsExactly(older, newer);

        pinInterviewSession.execute(older, false);
        assertThat(pinned(older)).isFalse();
        assertThat(sessionIds(accountId)).containsExactly(newer, older);
    }

    @Test
    void anotherAccountCannotPinOrDeleteSomeoneElsesSession() {
        long owner = AccountFixtures.create(jdbcTemplate, "pin-owner");
        long sessionId = createSession(owner);
        long intruder = AccountFixtures.create(jdbcTemplate, "pin-intruder");
        AccountFixtures.authenticate(intruder);

        assertThatThrownBy(() -> pinInterviewSession.execute(sessionId, true))
            .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> deleteInterviewSession.execute(sessionId))
            .isInstanceOf(BusinessException.class);

        assertThat(count("SELECT COUNT(*) FROM interview_session WHERE id = ?", sessionId)).isEqualTo(1);
        assertThat(pinned(sessionId)).isFalse();
    }

    @Test
    void pendingClosingAssessmentBlocksTheReportJobUntilItIsScored() {
        long accountId = AccountFixtures.create(jdbcTemplate, "finish-assessment");
        long sessionId = createSession(accountId);
        insert("INSERT INTO interview_stage (session_id, stage_name, boundary_seq) VALUES (?, 'closing', -1)", sessionId);
        long messageId = insert(
            "INSERT INTO interview_message (session_id, role, content, seq_num, assessment_status) VALUES (?, 'user', ?, 0, 'PENDING')",
            sessionId, "closing answer");
        AccountFixtures.authenticate(accountId);

        assertThatThrownBy(() -> finishInterview.execute(sessionId))
            .isInstanceOfSatisfying(BusinessException.class, exception -> {
                assertThat(exception.getStatus()).isEqualTo(409);
                assertThat(exception.getCode()).isEqualTo("assessment_pending");
            });
        assertThat(jdbcTemplate.queryForObject(
            "SELECT status FROM interview_session WHERE id = ?", String.class, sessionId)).isEqualTo("ongoing");
        assertThat(count("SELECT COUNT(*) FROM background_job WHERE subject_id = ? AND type = ?",
            sessionId, JobTypes.REPORT_GENERATE)).isZero();

        jdbcTemplate.update(
            "UPDATE interview_message SET assessment_status = 'SCORED', score = 8 WHERE id = ?", messageId);
        FinishInterviewResult result = finishInterview.execute(sessionId);

        assertThat(result.status()).isEqualTo("generating");
        assertThat(result.jobId()).isNotBlank();
        assertThat(count("SELECT COUNT(*) FROM background_job WHERE subject_id = ? AND type = ?",
            sessionId, JobTypes.REPORT_GENERATE)).isEqualTo(1);
    }

    @Test
    void explicitBoundariesAndAwaitingAnswerSurviveRealDatabaseRoundTrips() {
        long accountId = AccountFixtures.create(jdbcTemplate, "stage-boundary");
        long sessionId = createSession(accountId);
        long warmupId = insert(
            "INSERT INTO interview_stage (session_id, stage_name, boundary_seq, awaiting_answer) VALUES (?, 'warmup', -1, FALSE)",
            sessionId);
        AccountFixtures.authenticate(accountId);

        interviewMessageService.insertUserMessage(sessionId, "introduction");
        assertThat(awaitingAnswer(warmupId)).isFalse();
        interviewMessageService.insertAssistantMessage(sessionId, "candidate introduction");
        assertThat(awaitingAnswer(warmupId)).isTrue();
        interviewMessageService.insertUserMessage(sessionId, "additional context");
        assertThat(awaitingAnswer(warmupId)).isFalse();
        interviewTurnCompletion.complete(sessionId, "follow-up answer");

        assertThat(awaitingAnswer(warmupId)).isFalse();
        assertThat(jdbcTemplate.queryForObject(
            "SELECT ended_at IS NOT NULL FROM interview_stage WHERE id = ?", Boolean.class, warmupId)).isTrue();
        assertThat(jdbcTemplate.queryForObject(
            "SELECT boundary_seq FROM interview_stage WHERE session_id = ? AND stage_name = 'technical'",
            Integer.class, sessionId)).isEqualTo(3);
        assertThat(jdbcTemplate.queryForObject(
            "SELECT awaiting_answer FROM interview_stage WHERE session_id = ? AND stage_name = 'technical'",
            Boolean.class, sessionId)).isFalse();
        assertThat(jdbcTemplate.queryForList(
            "SELECT seq_num FROM interview_message WHERE session_id = ? ORDER BY seq_num",
            Integer.class, sessionId)).containsExactly(0, 1, 2, 3, 4);
    }

    private boolean awaitingAnswer(long stageId) {
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject(
            "SELECT awaiting_answer FROM interview_stage WHERE id = ?", Boolean.class, stageId));
    }

    private List<Long> sessionIds(long accountId) {
        return interviewSessionRepository.listByUser(accountId).stream()
            .map(InterviewSession::getId)
            .toList();
    }

    private boolean pinned(long sessionId) {
        Boolean value = jdbcTemplate.queryForObject(
            "SELECT pinned_at IS NOT NULL FROM interview_session WHERE id = ?", Boolean.class, sessionId);
        return Boolean.TRUE.equals(value);
    }

    private long count(String sql, Object... parameters) {
        Long value = jdbcTemplate.queryForObject(sql, Long.class, parameters);
        return value == null ? 0L : value;
    }

    @AfterEach
    void forgetTheAuthenticatedAccount() {
        AccountFixtures.clearAuthentication();
    }

    private long createSession(long accountId) {
        return InterviewDataFixtures.sessionInProgress(jdbcTemplate, accountId, LocalDateTime.now());
    }

    private long createSession(long accountId, LocalDateTime createdAt) {
        return InterviewDataFixtures.sessionInProgress(jdbcTemplate, accountId, createdAt);
    }

    private long insert(String sql, Object... params) {
        return InterviewDataFixtures.insert(jdbcTemplate, sql, params);
    }
}
