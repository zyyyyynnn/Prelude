package com.prelude.llm;

import com.prelude.BusinessException;
import com.prelude.llm.application.port.ModelProfileStore;
import com.prelude.llm.application.port.ProviderCredentialStore;
import com.prelude.test.LlmFixtures;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DeadlockLoserDataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * What a lost race for the account's one profile row answers with.
 *
 * <p>Which shape the database uses is not something a test can choose — two first-saves can come
 * back as a duplicate key or, because both hold a gap lock from the "no row yet" lookup and then
 * want an insert-intention lock in the same gap, as a deadlock. So the mapping is pinned directly
 * here rather than by racing and hoping the other branch is exercised.
 */
class ModelProfileRaceTest {

    @Test
    void aDuplicateKeyOnTheFirstProfileIsAnsweredAsARevisionConflict() {
        assertThatThrownBy(() -> saveWithFailingInsert(new DuplicateKeyException("duplicate")))
            .isInstanceOf(BusinessException.class)
            .extracting(error -> ((BusinessException) error).getCode())
            .isEqualTo("revision_conflict");
    }

    @Test
    void aDeadlockOnTheFirstProfileIsAnsweredTheSameWay() {
        assertThatThrownBy(() -> saveWithFailingInsert(
            new DeadlockLoserDataAccessException("deadlock found", new RuntimeException())))
            .isInstanceOf(BusinessException.class)
            .extracting(error -> ((BusinessException) error).getCode())
            .isEqualTo("revision_conflict");
    }

    /* A lock wait that simply timed out is some other transaction holding the row, not a
       conflicting save, and must not be reported as one. */
    @Test
    void aLockWaitTimeoutIsNotRebrandedAsAConflict() {
        CannotAcquireLockException timeout = new CannotAcquireLockException("lock wait timeout");
        assertThatThrownBy(() -> saveWithFailingInsert(timeout)).isSameAs(timeout);
    }

    private void saveWithFailingInsert(RuntimeException persistenceFailure) {
        ProviderCredentialStore credentials = Mockito.mock(ProviderCredentialStore.class);
        ModelProfileStore profiles = Mockito.mock(ModelProfileStore.class);
        Mockito.when(profiles.findActiveByAccount(Mockito.any())).thenReturn(Optional.empty());
        Mockito.when(profiles.findActiveForUpdate(Mockito.any())).thenReturn(Optional.empty());
        Mockito.when(profiles.insert(Mockito.any())).thenThrow(persistenceFailure);

        PlatformTransactionManager transactionManager = Mockito.mock(PlatformTransactionManager.class);
        Mockito.when(transactionManager.getTransaction(Mockito.any()))
            .thenReturn(new SimpleTransactionStatus());
        ObjectMapper objectMapper = new ObjectMapper();
        ModelCapabilityJson capabilityJson = new ModelCapabilityJson(objectMapper);
        ModelCapabilityCatalog catalog = new ModelCapabilityCatalog();

        ModelProfileService service = new ModelProfileService(
            credentials,
            profiles,
            Mockito.mock(ProviderSecretCipher.class),
            Mockito.mock(ProviderCredentialResolver.class),
            catalog,
            new ReasoningLevels(),
            Mockito.mock(CustomModelCapabilityDiscovery.class),
            Mockito.mock(CustomModelCatalogClient.class),
            Mockito.mock(CustomLlmEgressPolicy.class),
            capabilityJson,
            objectMapper,
            new TransactionTemplate(transactionManager));

        service.saveConfiguration(7L, LlmFixtures.saveConfigurationCommand(
            "deepseek", "deepseek-v4-pro", null, null, "AUTO", null, List.of()));
    }
}
