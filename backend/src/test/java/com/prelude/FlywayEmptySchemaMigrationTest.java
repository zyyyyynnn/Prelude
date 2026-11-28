package com.prelude;

import static org.assertj.core.api.Assertions.assertThat;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "PRELUDE_FLYWAY_SMOKE", matches = "true")
class FlywayEmptySchemaMigrationTest {

    @Test
    void migratesAnEmptyDatabaseToTheCurrentVersion() {
        Flyway flyway = Flyway.configure()
            .dataSource(
                System.getenv("PRELUDE_FLYWAY_SMOKE_URL"),
                System.getenv("PRELUDE_FLYWAY_SMOKE_USERNAME"),
                System.getenv("PRELUDE_FLYWAY_SMOKE_PASSWORD")
            )
            .locations("classpath:db/migration")
            .validateOnMigrate(true)
            .load();

        MigrateResult result = flyway.migrate();

        assertThat(result.migrationsExecuted).isPositive();
        assertThat(flyway.info().pending()).isEmpty();
        assertThat(flyway.info().current()).isNotNull();
    }
}
