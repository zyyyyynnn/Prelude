package com.prelude.interview.application;

import com.prelude.BusinessException;
import com.prelude.context.RetrievalChunkStore;
import com.prelude.context.RetrievalPort;
import com.prelude.interview.application.port.InterviewSessionRepository;
import com.prelude.interview.domain.InterviewSession;
import com.prelude.test.AccountFixtures;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;

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
    private PinInterviewSession pinInterviewSession;

    @Autowired
    private InterviewSessionRepository interviewSessionRepository;

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
        insert("INSERT INTO interview_stage (session_id, stage_name) VALUES (?, 'warmup')", sessionId);
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

    private long count(String sql, long param) {
        Long value = jdbcTemplate.queryForObject(sql, Long.class, param);
        return value == null ? 0L : value;
    }

    private long createSession(long accountId) {
        return createSession(accountId, LocalDateTime.now());
    }

    private long createSession(long accountId, LocalDateTime createdAt) {
        long nano = System.nanoTime();
        long resumeId = insert(
            "INSERT INTO resume (account_id, file_name, raw_text, parsed_skills, parsed_projects) VALUES (?, ?, ?, ?, ?)",
            accountId, "resume-" + nano + ".pdf", "resume", "[]", "[]");
        long positionId = insert(
            "INSERT INTO position_template (account_id, name, system_prompt) VALUES (?, ?, ?)",
            accountId, "position-" + nano, "system");
        List<Long> storedSnapshots = jdbcTemplate.queryForList(
            "SELECT id FROM model_execution_snapshot WHERE account_id = ? ORDER BY id LIMIT 1",
            Long.class, accountId);
        long snapshotId = storedSnapshots.isEmpty() ? createSnapshot(accountId) : storedSnapshots.getFirst();
        return insert(
            "INSERT INTO interview_session (account_id, resume_id, position_id, target_position, model_execution_snapshot_id, status, created_at)"
                + " VALUES (?, ?, ?, ?, ?, 'ongoing', ?)",
            accountId, resumeId, positionId, "position-" + nano, snapshotId, createdAt);
    }

    private long createSnapshot(long accountId) {
        long profileId = insert(
            "INSERT INTO model_profile (account_id, provider, model, reasoning_level, effective_parameters_json, fallback_capabilities_json)"
                + " VALUES (?, ?, ?, ?, ?, ?)",
            accountId, "deepseek", "deepseek-v4-pro", "AUTO", "{\"maxOutputTokens\":4096}", "[]");
        return insert(
            "INSERT INTO model_execution_snapshot (account_id, profile_id, provider, model, reasoning_level,"
                + " effective_parameters_json, capability_version, model_capability_json, fallback_capabilities_json)"
                + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
            accountId, profileId, "deepseek", "deepseek-v4-pro", "AUTO", "{\"maxOutputTokens\":4096}",
            "2026-08-30",
            "{\"provider\":\"deepseek\",\"model\":\"deepseek-v4-pro\",\"reasoning\":true,"
                + "\"structuredOutput\":true,\"toolCalling\":true,\"streaming\":true,\"vision\":false,"
                + "\"multilingual\":true,\"longContext\":true,\"embedding\":false,\"nativeRealtimeVoice\":false,"
                + "\"supportedReasoningLevels\":[\"AUTO\",\"LOW\",\"HIGH\",\"MAX\"]}",
            "[]");
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
}
