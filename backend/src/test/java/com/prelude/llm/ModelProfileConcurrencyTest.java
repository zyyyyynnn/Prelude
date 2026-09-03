package com.prelude.llm;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.prelude.llm.persistence.ModelProfile;
import com.prelude.llm.persistence.ModelProfileMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DuplicateKeyException;

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
    private ModelProfileMapper profileMapper;

    @Autowired
    private com.prelude.identity.AccountMapper accountMapper;

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
        List<ModelProfile> stored = profileMapper.selectList(new LambdaQueryWrapper<ModelProfile>()
            .eq(ModelProfile::getAccountId, accountId));
        assertThat(stored).hasSize(1);
    }

    private boolean insertProfile(long accountId, String provider, String model) {
        ModelProfile profile = new ModelProfile();
        profile.setAccountId(accountId);
        profile.setProvider(provider);
        profile.setModel(model);
        profile.setReasoningLevel("AUTO");
        profile.setEffectiveParametersJson("{\"maxOutputTokens\":4096}");
        profile.setFallbackModelsJson("[]");
        try {
            profileMapper.insert(profile);
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
        com.prelude.identity.Account account = new com.prelude.identity.Account();
        account.setUsername("llm-profile-race-" + System.nanoTime());
        account.setRevision(0L);
        accountMapper.insert(account);
        return account.getId();
    }
}
