package com.prelude.test;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;

/**
 * Rows a resume-workspace test needs before it can assert anything: an account, an imported
 * resume, a frozen model snapshot and a conversation hung off them.
 *
 * <p>The resume is part of the fixture because the workspace refuses to run an instruction for
 * a conversation with no document to patch — that refusal is a behaviour under test, not an
 * obstacle to work around.
 */
public final class ResumeDataFixtures {

    private ResumeDataFixtures() {
    }

    /** The three ids one workspace scenario is built on, plus the revision they start from. */
    public record Workspace(long accountId, long resumeId, long conversationId, int revision) {
    }

    public static Workspace workspace(JdbcTemplate jdbc, String prefix) {
        long accountId = AccountFixtures.create(jdbc, prefix);
        return workspace(jdbc, accountId, prefix);
    }

    public static Workspace workspace(JdbcTemplate jdbc, long accountId, String prefix) {
        long nano = System.nanoTime();
        long resumeId = insert(jdbc,
            "INSERT INTO resume (account_id, file_name, raw_text, parsed_skills, parsed_projects) VALUES (?, ?, ?, ?, ?)",
            accountId, "resume-" + nano + ".pdf",
            "后端开发工程师\n负责订单服务，接口 P99 从 480ms 降到 210ms",
            "[\"Java\",\"MySQL\"]",
            "[{\"name\":\"订单服务\",\"description\":\"支撑日均 200 万单\"}]");
        long conversationId = insert(jdbc,
            "INSERT INTO resume_conversation (account_id, resume_id, title, created_at, updated_at)"
                + " VALUES (?, ?, ?, ?, ?)",
            accountId, resumeId, "新的简历工作", LocalDateTime.now(), LocalDateTime.now());
        int revision = seedRevision(jdbc, resumeId);
        return new Workspace(accountId, resumeId, conversationId, revision);
    }

    /** A conversation with no resume attached, which is what the run refuses to work on. */
    public static long unattachedConversation(JdbcTemplate jdbc, long accountId) {
        return insert(jdbc,
            "INSERT INTO resume_conversation (account_id, resume_id, title, created_at, updated_at)"
                + " VALUES (?, NULL, ?, ?, ?)",
            accountId, "未挂接", LocalDateTime.now(), LocalDateTime.now());
    }

    public static long snapshot(JdbcTemplate jdbc, long accountId) {
        List<Long> stored = jdbc.queryForList(
            "SELECT id FROM model_execution_snapshot WHERE account_id = ? ORDER BY id LIMIT 1",
            Long.class, accountId);
        if (!stored.isEmpty()) {
            return stored.getFirst();
        }
        long profileId = insert(jdbc,
            "INSERT INTO model_profile (account_id, provider, model, reasoning_level,"
                + " effective_parameters_json, fallback_capabilities_json) VALUES (?, ?, ?, ?, ?, ?)",
            accountId, "deepseek", "deepseek-v4-pro", "AUTO", "{\"maxOutputTokens\":4096}", "[]");
        return insert(jdbc,
            "INSERT INTO model_execution_snapshot (account_id, profile_id, provider, model,"
                + " reasoning_level, effective_parameters_json, capability_version,"
                + " model_capability_json, fallback_capabilities_json) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
            accountId, profileId, "deepseek", "deepseek-v4-pro", "AUTO", "{\"maxOutputTokens\":4096}",
            "2026-09-28",
            "{\"provider\":\"deepseek\",\"model\":\"deepseek-v4-pro\",\"reasoning\":true,"
                + "\"structuredOutput\":true,\"toolCalling\":true,\"streaming\":true,\"vision\":false,"
                + "\"multilingual\":true,\"longContext\":true,\"embedding\":false,"
                + "\"nativeRealtimeVoice\":false,\"supportedReasoningLevels\":[\"AUTO\"]}",
            "[]");
    }

    /** Revision 1 with the block ids the prompts and proposals name. */
    public static int seedRevision(JdbcTemplate jdbc, long resumeId) {
        int next = maxRevision(jdbc, resumeId) + 1;
        insert(jdbc,
            "INSERT INTO resume_revision (resume_id, revision_number, document_json, summary, origin, created_at)"
                + " VALUES (?, ?, ?, ?, 'imported', ?)",
            resumeId, next,
            """
                {"blocks":[\
                {"id":"概览-1","section":"概览","kind":"section","text":"概览"},\
                {"id":"概览-2","section":"概览","kind":"item","text":"后端开发工程师"},\
                {"id":"技能-1","section":"技能","kind":"bullet","text":"Java"},\
                {"id":"技能-2","section":"技能","kind":"bullet","text":"MySQL"},\
                {"id":"项目-1-标题","section":"项目","kind":"item","text":"订单服务"},\
                {"id":"项目-1-描述","section":"项目","kind":"bullet","text":"支撑日均 200 万单"}]}""",
            "导入 PDF 解析结果", LocalDateTime.now());
        return next;
    }

    public static int maxRevision(JdbcTemplate jdbc, long resumeId) {
        Integer stored = jdbc.queryForObject(
            "SELECT COALESCE(MAX(revision_number), 0) FROM resume_revision WHERE resume_id = ?",
            Integer.class, resumeId);
        return stored == null ? 0 : stored;
    }

    /** A pending proposal over a run the conversation already owns. */
    public static long proposal(
        JdbcTemplate jdbc,
        Workspace workspace,
        int baseRevision,
        String operationsJson,
        String status
    ) {
        long runId = insert(jdbc,
            "INSERT INTO resume_agent_run (turn_id, conversation_id, resume_id, base_revision,"
                + " model_execution_snapshot_id, prompt_id, status, started_at)"
                + " VALUES (?, ?, ?, ?, ?, 'resume.assistant', 'proposal_ready', ?)",
            turn(jdbc, workspace), workspace.conversationId(), workspace.resumeId(), baseRevision,
            snapshot(jdbc, workspace.accountId()), LocalDateTime.now());
        return insert(jdbc,
            "INSERT INTO resume_patch_proposal (run_id, resume_id, conversation_id, base_revision,"
                + " affected_block_ids, reason, operations_json, status, created_at)"
                + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
            runId, workspace.resumeId(), workspace.conversationId(), baseRevision,
            "[\"技能-1\"]", "把技能块改成一行一项", operationsJson, status, LocalDateTime.now());
    }

    public static long turn(JdbcTemplate jdbc, Workspace workspace) {
        return insert(jdbc,
            "INSERT INTO resume_turn (conversation_id, account_id, instruction, status,"
                + " queue_position, created_at) VALUES (?, ?, ?, 'running', ?, ?)",
            workspace.conversationId(), workspace.accountId(), "把技能写成一行的",
            nextQueuePosition(jdbc, workspace.conversationId()), LocalDateTime.now());
    }

    private static int nextQueuePosition(JdbcTemplate jdbc, long conversationId) {
        Integer stored = jdbc.queryForObject(
            "SELECT COALESCE(MAX(queue_position), 0) FROM resume_turn WHERE conversation_id = ?",
            Integer.class, conversationId);
        return (stored == null ? 0 : stored) + 1;
    }

    private static long insert(JdbcTemplate jdbc, String sql, Object... params) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement =
                connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            for (int index = 0; index < params.length; index++) {
                statement.setObject(index + 1, params[index]);
            }
            return statement;
        }, keyHolder);
        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("测试数据插入失败: " + sql);
        }
        return key.longValue();
    }
}
