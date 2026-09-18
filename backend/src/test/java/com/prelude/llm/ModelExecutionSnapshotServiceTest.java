package com.prelude.llm;

import com.prelude.test.ExceptionFixtures;
import com.prelude.test.LlmFixtures;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

class ModelExecutionSnapshotServiceTest {

    @Test
    void returnsTheFrozenModelOnlyInsideTheOwningAccount() {
        var mapper = LlmFixtures.mockSnapshotMapper();
        var snapshot = LlmFixtures.snapshotWithDefaults(42L, 7L, 1L, "deepseek", "deepseek-v4-flash", "HIGH", 4096);
        when(mapper.selectById(42L)).thenReturn(snapshot);
        ModelExecutionSnapshotService service = new ModelExecutionSnapshotService(
            null, mapper, null, null, null, null);

        assertThat(service.frozenConfiguration(7L, 42L))
            .extracting("model", "reasoningLevel")
            .containsExactly("deepseek-v4-flash", "HIGH");
        ExceptionFixtures.assertBusinessExceptionMessage(
            () -> service.frozenConfiguration(8L, 42L),
            "模型执行快照不存在");
    }
}
