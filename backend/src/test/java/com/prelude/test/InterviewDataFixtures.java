package com.prelude.test;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;

/**
 * Rows a MySQL-backed interview test needs before it can assert anything: a resume, a position,
 * a frozen model snapshot and a session in progress on top of them.
 *
 * <p>They exist because {@code interview_session} cascades to every dependent row and refuses
 * to hold one without them, so each test that exercises the lifecycle would otherwise re-write
 * the same four inserts.
 */
public final class InterviewDataFixtures {

    private InterviewDataFixtures() {
    }

    /** A session in progress for a fresh account, with everything it depends on. */
    public static long sessionInProgress(JdbcTemplate jdbc, String accountPrefix) {
        long accountId = AccountFixtures.create(jdbc, accountPrefix);
        return sessionInProgress(jdbc, accountId, LocalDateTime.now());
    }

    public static long sessionInProgress(JdbcTemplate jdbc, long accountId, LocalDateTime createdAt) {
        long nano = System.nanoTime();
        long resumeId = insert(jdbc,
            "INSERT INTO resume (account_id, file_name, raw_text, parsed_skills, parsed_projects) VALUES (?, ?, ?, ?, ?)",
            accountId, "resume-" + nano + ".pdf", "resume", "[]", "[]");
        long positionId = insert(jdbc,
            "INSERT INTO position_template (account_id, name, system_prompt) VALUES (?, ?, ?)",
            accountId, "position-" + nano, "system");
        long sessionId = insert(jdbc,
            "INSERT INTO interview_session (account_id, resume_id, position_id, target_position, model_execution_snapshot_id, status, created_at)"
                + " VALUES (?, ?, ?, ?, ?, 'ongoing', ?)",
            accountId, resumeId, positionId, "position-" + nano, snapshot(jdbc, accountId), createdAt);
        return sessionId;
    }

    /**
     * A snapshot the account already owns, or a fresh one frozen over a fresh profile. An account
     * may hold one profile row, so a second session on the same account has to reuse rather than
     * insert again.
     */
    private static long snapshot(JdbcTemplate jdbc, long accountId) {
        List<Long> stored = jdbc.queryForList(
            "SELECT id FROM model_execution_snapshot WHERE account_id = ? ORDER BY id LIMIT 1",
            Long.class, accountId);
        if (!stored.isEmpty()) return stored.getFirst();
        return insert(jdbc,
            "INSERT INTO model_execution_snapshot (account_id, profile_id, provider, model, reasoning_level,"
                + " effective_parameters_json, capability_version, model_capability_json, fallback_capabilities_json)"
                + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
            accountId, profile(jdbc, accountId), "deepseek", "deepseek-v4-pro", "AUTO",
            "{\"maxOutputTokens\":4096}",
            "2026-08-30",
            "{\"provider\":\"deepseek\",\"model\":\"deepseek-v4-pro\",\"reasoning\":true,"
                + "\"structuredOutput\":true,\"toolCalling\":true,\"streaming\":true,\"vision\":false,"
                + "\"multilingual\":true,\"longContext\":true,\"embedding\":false,\"nativeRealtimeVoice\":false,"
                + "\"supportedReasoningLevels\":[\"AUTO\",\"LOW\",\"HIGH\",\"MAX\"]}",
            "[]");
    }

    /** The account's one authoritative profile row, which the snapshot freezes a copy of. */
    private static long profile(JdbcTemplate jdbc, long accountId) {
        return insert(jdbc,
            "INSERT INTO model_profile (account_id, provider, model, reasoning_level, effective_parameters_json, fallback_capabilities_json)"
                + " VALUES (?, ?, ?, ?, ?, ?)",
            accountId, "deepseek", "deepseek-v4-pro", "AUTO", "{\"maxOutputTokens\":4096}", "[]");
    }

    public static long insert(JdbcTemplate jdbc, String sql, Object... params) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(con -> {
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
