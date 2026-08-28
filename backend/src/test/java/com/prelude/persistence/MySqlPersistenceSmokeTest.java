package com.prelude.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import com.prelude.identity.User;
import com.prelude.identity.UserMapper;
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
}
