package com.prelude.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.ResultSet;
import java.util.List;
import java.util.UUID;

import com.prelude.identity.Account;
import com.prelude.identity.AccountMapper;
import com.prelude.resume.api.port.ResumeContextPort;
import com.prelude.resume.application.port.ResumeRepository;
import org.apache.ibatis.session.SqlSessionFactory;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@EnabledIfEnvironmentVariable(named = "PRELUDE_MYSQL_SMOKE", matches = "true")
@SpringBootTest
class MySqlPersistenceSmokeTest {

    @Autowired
    private Flyway flyway;

    @Autowired
    private SqlSessionFactory sqlSessionFactory;

    @Autowired
    private AccountMapper accountMapper;

    @Autowired
    private ResumeRepository resumeRepository;

    @Autowired
    private ResumeContextPort resumeContextPort;

    @Test
    void migratesAnEmptyMySqlDatabaseToTheCurrentSchema() throws Exception {
        var appliedMigrations = flyway.info().applied();
        assertThat(appliedMigrations)
            .extracting(migration -> migration.getDescription())
            .containsExactly("establish prelude schema", "reference data");

        try (var session = sqlSessionFactory.openSession();
             var statement = session.getConnection().createStatement();
             var result = statement.executeQuery("SELECT COUNT(*) FROM flyway_schema_history")) {
            assertThat(result.next()).isTrue();
            assertThat(result.getInt(1)).isEqualTo(2);
        }
    }

    @Test
    void establishesTheCurrentAccountSchemaWithoutAuthenticationSessionTables() throws Exception {
        List<String> tables = queryColumn("SHOW TABLES");
        assertThat(tables).contains(
            "user_account", "oauth_binding", "asset", "attachment",
            "artifact", "artifact_version", "interview_session", "async_job",
            "EVENT_PUBLICATION");
        assertThat(tables)
            .doesNotContain("SPRING_SESSION", "SPRING_SESSION_ATTRIBUTES", "user", "user_weakness");
    }

    @Test
    void attachmentKeepsMetadataOnlyAndReferencesItsBinaryAsset() throws Exception {
        List<String> attachmentColumns = queryColumn(
            "SELECT COLUMN_NAME FROM information_schema.COLUMNS "
                + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'attachment'");
        assertThat(attachmentColumns).contains("account_id", "asset_id", "file_name");
        assertThat(attachmentColumns).doesNotContain("content", "user_id");

        List<String> attachmentDataTypes = queryColumn(
            "SELECT DATA_TYPE FROM information_schema.COLUMNS "
                + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'attachment'");
        assertThat(attachmentDataTypes).doesNotContain("longblob");

        List<String> accountColumns = queryColumn(
            "SELECT COLUMN_NAME FROM information_schema.COLUMNS "
                + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'user_account'");
        assertThat(accountColumns).contains("password_hash", "revision", "last_operation_id", "email");
    }

    @Test
    void explicitlyClearsNullableLlmConfigurationFields() {
        Account account = new Account();
        account.setUsername("migration-smoke-" + UUID.randomUUID());
        account.setPasswordHash("not-used");
        account.setRevision(0L);
        account.setLlmProvider("deepseek");
        account.setLlmModel("deepseek-v4-flash");
        account.setLlmBaseUrl("https://example.com/v1");
        account.setLlmApiKeyEncrypted("encrypted");
        account.setLlmMaxTokens(4096);
        account.setLlmThinkingDepth("high");
        accountMapper.insert(account);

        accountMapper.updateLlmConfiguration(
            account.getId(),
            "deepseek",
            null,
            "deepseek-v4-flash",
            null,
            null,
            null
        );

        Account updated = accountMapper.selectById(account.getId());
        assertThat(updated.getLlmBaseUrl()).isNull();
        assertThat(updated.getLlmApiKeyEncrypted()).isNull();
        assertThat(updated.getLlmMaxTokens()).isNull();
        assertThat(updated.getLlmThinkingDepth()).isNull();
    }

    @Test
    void persistsResumeResourceAndBuildsInterviewContext() {
        Account account = new Account();
        account.setUsername("resume-smoke-" + UUID.randomUUID());
        account.setPasswordHash("not-used");
        account.setRevision(0L);
        accountMapper.insert(account);

        var stored = resumeRepository.create(new ResumeRepository.NewResume(
            account.getId(),
            "candidate.pdf",
            "Java 后端候选人原始简历",
            java.util.List.of("Java", "Spring Boot"),
            java.util.List.of(new ResumeRepository.ParsedProject("Prelude", "模拟面试平台"))
        ));

        var reloaded = resumeRepository.findById(stored.id()).orElseThrow();
        assertThat(reloaded.rawText()).isEqualTo("Java 后端候选人原始简历");
        assertThat(reloaded.parsedSkills()).containsExactly("Java", "Spring Boot");
        assertThat(reloaded.parsedProjects())
            .containsExactly(new ResumeRepository.ParsedProject("Prelude", "模拟面试平台"));

        var projection = resumeContextPort.requireOwnedProjection(account.getId(), stored.id());
        assertThat(projection.plainText()).isEqualTo("Java 后端候选人原始简历");
        assertThat(projection.skills()).containsExactly("Java", "Spring Boot");
        assertThat(projection.projectsSummary()).containsExactly("Prelude：模拟面试平台");
    }

    private List<String> queryColumn(String sql) throws Exception {
        try (var session = sqlSessionFactory.openSession();
             var statement = session.getConnection().createStatement();
             ResultSet result = statement.executeQuery(sql)) {
            List<String> values = new java.util.ArrayList<>();
            while (result.next()) {
                values.add(result.getString(1));
            }
            return values;
        }
    }
}
