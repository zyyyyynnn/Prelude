package com.prelude.llm;

import com.prelude.BusinessException;
import com.prelude.llm.api.LlmPort;
import com.prelude.test.AccountFixtures;
import com.prelude.test.LlmFixtures;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Two writers claiming the one authoritative profile row for an account, against real MySQL.
 *
 * <p>The unique index is only half of the contract: the service has to translate losing that race
 * into the conflict the client is told to refresh against, rather than a raw persistence error. An
 * earlier revision of this test inserted the rows itself and caught {@code DuplicateKeyException}
 * itself, so it proved the index existed and never ran the branch that owns the behaviour.
 */
@EnabledIfEnvironmentVariable(named = "PRELUDE_MYSQL_SMOKE", matches = "true")
@SpringBootTest(properties = "spring.rabbitmq.listener.simple.auto-startup=false")
class ModelProfileConcurrencyTest {

    @Autowired
    private LlmPort llmPort;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final ExecutorService executor = Executors.newFixedThreadPool(2);

    @AfterEach
    void shutdownExecutor() {
        executor.shutdownNow();
    }

    @Test
    void concurrentFirstProfilesElectOneWinnerAndAnswerTheLoserWithAConflict() throws Exception {
        long accountId = AccountFixtures.create(jdbcTemplate, "llm-profile-race");
        CyclicBarrier start = new CyclicBarrier(2);

        List<SaveOutcome> outcomes = List.of(
                executor.submit(raced(start, () -> firstSave(accountId, "deepseek", "deepseek-v4-pro"))),
                executor.submit(raced(start, () -> firstSave(accountId, "deepseek", "deepseek-v4-flash"))))
            .stream()
            .map(this::get)
            .toList();

        assertThat(outcomes.stream().filter(SaveOutcome::saved).count()).isEqualTo(1);
        assertThat(outcomes).extracting(SaveOutcome::code).containsExactlyInAnyOrder(
            "saved", "revision_conflict");

        // The loser's write must not have landed either, and the row that exists is the winner's.
        assertThat(jdbcTemplate.queryForObject(
            "SELECT count(*) FROM model_profile WHERE account_id = ?", Integer.class, accountId))
            .isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
            "SELECT model FROM model_profile WHERE account_id = ?", String.class, accountId))
            .isEqualTo(outcomes.stream().filter(SaveOutcome::saved).findFirst().orElseThrow().model());
    }

    private SaveOutcome firstSave(long accountId, String provider, String model) {
        try {
            llmPort.saveConfiguration(accountId,
                LlmFixtures.saveConfigurationCommand(provider, model, null, null, "AUTO", null, List.of()));
            return new SaveOutcome(true, model, "saved");
        } catch (BusinessException rejected) {
            return new SaveOutcome(false, model, rejected.getCode());
        }
    }

    private record SaveOutcome(boolean saved, String model, String code) {
    }

    private <T> Callable<T> raced(CyclicBarrier barrier, Callable<T> action) {
        return () -> {
            barrier.await();
            return action.call();
        };
    }

    private <T> T get(Future<T> future) {
        try {
            return future.get();
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
    }
}
