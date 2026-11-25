package com.prelude.interview.application;

import com.prelude.interview.application.repository.InterviewMessageRepository;
import com.prelude.interview.application.repository.InterviewSessionRepository;
import com.prelude.interview.application.repository.InterviewStageRepository;
import com.prelude.interview.domain.AssessmentStatus;
import com.prelude.interview.domain.InterviewMessage;
import com.prelude.interview.domain.InterviewSession;
import com.prelude.interview.domain.InterviewStage;
import com.prelude.interview.domain.InterviewStagePolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class InterviewTurnCompletionTest {

    private JdbcTemplate jdbc;
    private JdbcStageRepository stages;
    private InterviewTurnCompletion completion;

    @BeforeEach
    void setUp() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.h2.Driver");
        dataSource.setUrl("jdbc:h2:mem:turn-completion-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
        dataSource.setUsername("sa");
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("CREATE TABLE interview_message (id BIGINT AUTO_INCREMENT PRIMARY KEY, session_id BIGINT, role VARCHAR(16), content VARCHAR(1000), seq_num INT, assessment_status VARCHAR(16), created_at TIMESTAMP)");
        jdbc.execute("CREATE TABLE interview_stage (id BIGINT AUTO_INCREMENT PRIMARY KEY, session_id BIGINT, stage_name VARCHAR(32), started_at TIMESTAMP, ended_at TIMESTAMP)");
        jdbc.update("INSERT INTO interview_message (session_id, role, content, seq_num, created_at) VALUES (7, 'system', 'opening', 0, CURRENT_TIMESTAMP)");
        jdbc.update("INSERT INTO interview_stage (session_id, stage_name, started_at) VALUES (7, 'warmup', CURRENT_TIMESTAMP)");

        InterviewSessionRepository sessions = mock(InterviewSessionRepository.class);
        when(sessions.lockAppendOrder(7L)).thenReturn("ongoing");
        InterviewMessageService messages = new InterviewMessageService(new JdbcMessageRepository(), sessions);
        stages = new JdbcStageRepository();
        InterviewStageManager stageManager = new InterviewStageManager(
            stages, sessions, new JdbcMessageRepository(), messages, new InterviewStagePolicy());
        InterviewTurnCompletion target = new InterviewTurnCompletion(messages, stageManager);
        DataSourceTransactionManager transactionManager = new DataSourceTransactionManager(dataSource);
        ProxyFactory proxyFactory = new ProxyFactory(target);
        proxyFactory.setProxyTargetClass(true);
        proxyFactory.addAdvice(new TransactionInterceptor(transactionManager, new AnnotationTransactionAttributeSource()));
        completion = (InterviewTurnCompletion) proxyFactory.getProxy();
    }

    @Test
    void assistantMessageAndStageTransitionCommitTogether() {
        completion.complete(7L, "first answer");

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM interview_message", Integer.class)).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT stage_name FROM interview_stage WHERE id = 2", String.class))
            .isEqualTo("technical");
        assertThat(jdbc.queryForObject("SELECT assessment_status FROM interview_message WHERE role = 'assistant'", String.class))
            .isNull();
    }

    @Test
    void stageFailureRollsBackTheAssistantMessage() {
        stages.failNextAdd = true;

        assertThatThrownBy(() -> completion.complete(7L, "first answer"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("stage insert failed");

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM interview_message", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM interview_stage", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT ended_at FROM interview_stage WHERE id = 1", LocalDateTime.class)).isNull();
    }

    @Test
    void unknownStageRollsBackTheAssistantMessage() {
        jdbc.update("UPDATE interview_stage SET stage_name = 'unknown' WHERE id = 1");

        assertThatThrownBy(() -> completion.complete(7L, "first answer"))
            .hasMessageContaining("无效的面试阶段");

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM interview_message", Integer.class)).isEqualTo(1);
    }

    private class JdbcMessageRepository implements InterviewMessageRepository {
        @Override
        public int add(InterviewMessage message) {
            jdbc.update("INSERT INTO interview_message (session_id, role, content, seq_num, assessment_status, created_at) VALUES (?, ?, ?, ?, ?, ?)",
                message.getSessionId(), message.getRole(), message.getContent(), message.getSeqNum(),
                message.getAssessmentStatus() == null ? null : message.getAssessmentStatus().name(), LocalDateTime.now());
            message.setId(jdbc.queryForObject("SELECT MAX(id) FROM interview_message", Long.class));
            return 1;
        }

        @Override
        public int updateAssessmentIfPending(InterviewMessage message) {
            return jdbc.update("UPDATE interview_message SET score = ?, hint = ?, assessment_status = ?, assessment_error = ?"
                    + " WHERE id = ? AND assessment_status = 'PENDING'",
                message.getScore(), message.getHint(), message.getAssessmentStatus().name(),
                message.getAssessmentError(), message.getId());
        }

        @Override
        public List<InterviewMessage> findPendingForJudge(LocalDateTime cutoff, int limit) {
            return jdbc.query("SELECT * FROM interview_message WHERE role = 'user' AND assessment_status = 'PENDING'"
                    + " AND created_at <= ? ORDER BY created_at, id LIMIT " + limit,
                (rs, rowNum) -> {
                    InterviewMessage message = new InterviewMessage();
                    message.setId(rs.getLong("id"));
                    message.setAssessmentStatus(AssessmentStatus.PENDING);
                    message.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime());
                    return message;
                }, cutoff);
        }

        @Override
        public InterviewMessage findLatest(Long sessionId) {
            return findLatestForAppend(sessionId);
        }

        @Override
        public InterviewMessage findLatestForAppend(Long sessionId) {
            return jdbc.query("SELECT * FROM interview_message WHERE session_id = ? ORDER BY seq_num DESC LIMIT 1",
                (rs, rowNum) -> {
                    InterviewMessage message = new InterviewMessage();
                    message.setId(rs.getLong("id"));
                    message.setSessionId(rs.getLong("session_id"));
                    message.setRole(rs.getString("role"));
                    message.setContent(rs.getString("content"));
                    message.setSeqNum(rs.getInt("seq_num"));
                    String status = rs.getString("assessment_status");
                    message.setAssessmentStatus(status == null ? null : AssessmentStatus.valueOf(status));
                    message.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime());
                    return message;
                }, sessionId).stream().findFirst().orElse(null);
        }

        @Override
        public InterviewMessage findById(Long messageId) {
            return null;
        }

        @Override
        public List<InterviewMessage> listBySession(Long sessionId) {
            return jdbc.query("SELECT * FROM interview_message WHERE session_id = ? ORDER BY seq_num",
                (rs, rowNum) -> {
                    InterviewMessage message = new InterviewMessage();
                    message.setId(rs.getLong("id"));
                    message.setSessionId(rs.getLong("session_id"));
                    message.setRole(rs.getString("role"));
                    message.setContent(rs.getString("content"));
                    message.setSeqNum(rs.getInt("seq_num"));
                    message.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime());
                    return message;
                }, sessionId);
        }

        @Override
        public long countConversationMessages(Long sessionId) {
            return 0;
        }

        @Override
        public long countPendingAssessments(Long sessionId) {
            Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM interview_message WHERE session_id = ? AND role = 'user' AND assessment_status = 'PENDING'",
                Integer.class, sessionId);
            return count == null ? 0 : count;
        }
    }

    private class JdbcStageRepository implements InterviewStageRepository {
        private boolean failNextAdd;

        @Override
        public int add(InterviewStage stage) {
            if (failNextAdd) {
                failNextAdd = false;
                throw new IllegalStateException("stage insert failed");
            }
            jdbc.update("INSERT INTO interview_stage (session_id, stage_name, started_at) VALUES (?, ?, ?)",
                stage.getSessionId(), stage.getStageName(), stage.getStartedAt());
            stage.setId(jdbc.queryForObject("SELECT MAX(id) FROM interview_stage", Long.class));
            return 1;
        }

        @Override
        public int update(InterviewStage stage) {
            return jdbc.update("UPDATE interview_stage SET ended_at = ? WHERE id = ?", stage.getEndedAt(), stage.getId());
        }

        @Override
        public InterviewStage findCurrent(Long sessionId) {
            return jdbc.query("SELECT * FROM interview_stage WHERE session_id = ? AND ended_at IS NULL ORDER BY id DESC LIMIT 1",
                (rs, rowNum) -> stage(rs), sessionId).stream().findFirst().orElse(null);
        }

        @Override
        public InterviewStage findCurrentForUpdate(Long sessionId) {
            return findCurrent(sessionId);
        }

        @Override
        public InterviewStage findLatest(Long sessionId) {
            return jdbc.query("SELECT * FROM interview_stage WHERE session_id = ? ORDER BY id DESC LIMIT 1",
                (rs, rowNum) -> stage(rs), sessionId).stream().findFirst().orElse(null);
        }

        @Override
        public InterviewStage findLatestForUpdate(Long sessionId) {
            return findLatest(sessionId);
        }

        @Override
        public List<InterviewStage> listBySession(Long sessionId) {
            return List.of();
        }

        private InterviewStage stage(java.sql.ResultSet rs) throws java.sql.SQLException {
            InterviewStage stage = new InterviewStage();
            stage.setId(rs.getLong("id"));
            stage.setSessionId(rs.getLong("session_id"));
            stage.setStageName(rs.getString("stage_name"));
            stage.setStartedAt(rs.getTimestamp("started_at").toLocalDateTime());
            var endedAt = rs.getTimestamp("ended_at");
            stage.setEndedAt(endedAt == null ? null : endedAt.toLocalDateTime());
            return stage;
        }
    }
}
