package com.prelude.llm;

import com.prelude.test.AccountFixtures;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "PRELUDE_MYSQL_SMOKE", matches = "true")
@SpringBootTest(properties = "spring.rabbitmq.listener.simple.auto-startup=false")
class ModelProfileConcurrencyTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final ExecutorService executor = Executors.newFixedThreadPool(2);

    @AfterEach
    void shutdownExecutor() {
        executor.shutdownNow();
    }

    @Test
    void concurrentFirstProfilesForDifferentProvidersStillProduceOneAuthoritativeRow() throws Exception {
        long accountId = createAccount();
        CyclicBarrier start = new CyclicBarrier(2);

        List<Future<Boolean>> writes = List.of(
            executor.submit(raced(start, () -> insertProfile(accountId, "deepseek", "deepseek-v4-pro"))),
            executor.submit(raced(start, () -> insertProfile(
                accountId, CustomLlmProtocol.OPENAI_CHAT_COMPLETIONS.providerKey(), "account-model")))
        );

        assertThat(writes.stream().map(this::get).filter(Boolean::booleanValue).count()).isEqualTo(1);
        Integer count = jdbcTemplate.queryForObject(
            "SELECT count(*) FROM model_profile WHERE account_id = ?", Integer.class, accountId);
        assertThat(count).isEqualTo(1);
    }

    private boolean insertProfile(long accountId, String provider, String model) {
        try {
            jdbcTemplate.update(
                "INSERT INTO model_profile (account_id, provider, model, reasoning_level, effective_parameters_json, fallback_capabilities_json, updated_at) VALUES (?, ?, ?, 'AUTO', '{\"maxOutputTokens\":4096}', '[]', NOW())",
                accountId, provider, model);
            return true;
        } catch (DuplicateKeyException expectedRaceLoser) {
            return false;
        }
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

    private long createAccount() {
        return AccountFixtures.create(jdbcTemplate, "llm-profile-race");
    }
}
