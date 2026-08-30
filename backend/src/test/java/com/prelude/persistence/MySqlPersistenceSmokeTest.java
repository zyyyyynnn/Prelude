package com.prelude.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import com.prelude.identity.User;
import com.prelude.identity.UserMapper;
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
    private UserMapper userMapper;

    @Autowired
    private ResumeRepository resumeRepository;

    @Autowired
    private ResumeContextPort resumeContextPort;

    @Test
    void migratesAnEmptyMySqlDatabaseAndStartsMybatis() throws Exception {
        var appliedMigrations = flyway.info().applied();
        assertThat(appliedMigrations).hasSize(2);
        assertThat(appliedMigrations)
            .extracting(migration -> migration.getDescription())
            .containsExactly("establish prelude schema", "establish reference data");

        try (var session = sqlSessionFactory.openSession();
             var statement = session.getConnection().createStatement();
             var result = statement.executeQuery("SELECT COUNT(*) FROM flyway_schema_history")) {
            assertThat(result.next()).isTrue();
            assertThat(result.getInt(1)).isEqualTo(2);
        }
    }

    @Test
    void explicitlyClearsNullableLlmConfigurationFields() {
        User user = new User();
        user.setUsername("migration-smoke-" + UUID.randomUUID());
        user.setPassword("not-used");
        user.setLlmProvider("deepseek");
        user.setLlmModel("deepseek-v4-flash");
        user.setLlmBaseUrl("https://example.com/v1");
        user.setLlmApiKeyEncrypted("encrypted");
        user.setLlmMaxTokens(4096);
        user.setLlmThinkingDepth("high");
        userMapper.insert(user);

        userMapper.updateLlmConfiguration(
            user.getId(),
            "deepseek",
            null,
            "deepseek-v4-flash",
            null,
            null,
            null
        );

        User updated = userMapper.selectById(user.getId());
        assertThat(updated.getLlmBaseUrl()).isNull();
        assertThat(updated.getLlmApiKeyEncrypted()).isNull();
        assertThat(updated.getLlmMaxTokens()).isNull();
        assertThat(updated.getLlmThinkingDepth()).isNull();
    }

    @Test
    void persistsResumeResourceAndBuildsInterviewContext() {
        User user = new User();
        user.setUsername("resume-smoke-" + UUID.randomUUID());
        user.setPassword("not-used");
        userMapper.insert(user);

        var stored = resumeRepository.create(new ResumeRepository.NewResume(
            user.getId(),
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

        var projection = resumeContextPort.requireOwnedProjection(user.getId(), stored.id());
        assertThat(projection.plainText()).isEqualTo("Java 后端候选人原始简历");
        assertThat(projection.skills()).containsExactly("Java", "Spring Boot");
        assertThat(projection.projectsSummary()).containsExactly("Prelude：模拟面试平台");
    }
}
