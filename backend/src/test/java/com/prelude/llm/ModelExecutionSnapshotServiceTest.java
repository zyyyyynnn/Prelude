package com.prelude.llm;

import com.prelude.llm.api.LlmPort;
import com.prelude.llm.application.port.ModelExecutionSnapshotStore;
import com.prelude.llm.application.port.ModelExecutionSnapshotStore.SnapshotRow;
import com.prelude.llm.application.port.ModelProfileStore;
import com.prelude.llm.application.port.ModelProfileStore.ProfileRow;
import com.prelude.test.ExceptionFixtures;
import com.prelude.test.LlmFixtures;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.ObjectMapper;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ModelExecutionSnapshotServiceTest {

    @Test
    void freezesTheRequestedModelAndReasoningInsteadOfTheProfileValues() {
        var profileStore = mock(ModelProfileStore.class);
        var snapshotStore = mock(ModelExecutionSnapshotStore.class);
        var objectMapper = new ObjectMapper();
        when(profileStore.findActiveByAccount(7L)).thenReturn(Optional.of(new ProfileRow(
            19L, 7L, "deepseek", "deepseek-v4-pro", null, "AUTO",
            "{\"maxOutputTokens\":4096}", "{}", "[]", null)));
        when(snapshotStore.insert(any(SnapshotRow.class))).thenReturn(42L);
        var service = new ModelExecutionSnapshotService(
            profileStore, snapshotStore, new ModelCapabilityCatalog(), new ReasoningLevels(),
            new ModelCapabilityJson(objectMapper), objectMapper);

        var snapshotId = service.freeze(new LlmPort.FreezeSnapshotCommand(
            7L, "HIGH", "deepseek-v4-flash"));

        assertThat(snapshotId.snapshotId()).isEqualTo(42L);
        var frozen = ArgumentCaptor.forClass(SnapshotRow.class);
        verify(snapshotStore).insert(frozen.capture());
        assertThat(frozen.getValue().model()).isEqualTo("deepseek-v4-flash");
        assertThat(frozen.getValue().reasoningLevel()).isEqualTo("HIGH");
    }

    @Test
    void returnsTheFrozenModelOnlyInsideTheOwningAccount() {
        var store = mock(ModelExecutionSnapshotStore.class);
        var snapshot = LlmFixtures.snapshotWithDefaults(42L, 7L, 1L, "deepseek", "deepseek-v4-flash", "HIGH", 4096);
        when(store.findById(42L)).thenReturn(snapshot);
        ModelExecutionSnapshotService service = new ModelExecutionSnapshotService(
            null, store, null, null, null, null);

        assertThat(service.frozenConfiguration(7L, 42L))
            .extracting("model", "reasoningLevel")
            .containsExactly("deepseek-v4-flash", "HIGH");
        ExceptionFixtures.assertBusinessExceptionMessage(
            () -> service.frozenConfiguration(8L, 42L),
            "模型执行快照不存在");
    }
}
