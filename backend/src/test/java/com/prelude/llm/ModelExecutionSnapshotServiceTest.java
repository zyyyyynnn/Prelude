package com.prelude.llm;

import com.prelude.BusinessException;
import com.prelude.llm.persistence.ModelExecutionSnapshot;
import com.prelude.llm.persistence.ModelExecutionSnapshotMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ModelExecutionSnapshotServiceTest {

    @Test
    void returnsTheFrozenModelOnlyInsideTheOwningAccount() {
        ModelExecutionSnapshotMapper mapper = mock(ModelExecutionSnapshotMapper.class);
        ModelExecutionSnapshot snapshot = new ModelExecutionSnapshot();
        snapshot.setId(42L);
        snapshot.setAccountId(7L);
        snapshot.setModel("deepseek-v4-flash");
        when(mapper.selectById(42L)).thenReturn(snapshot);
        ModelExecutionSnapshotService service = new ModelExecutionSnapshotService(
            null, mapper, null, null, null);

        snapshot.setReasoningLevel("HIGH");

        assertThat(service.configurationFor(7L, 42L))
            .extracting("model", "reasoningLevel")
            .containsExactly("deepseek-v4-flash", "HIGH");
        assertThatThrownBy(() -> service.configurationFor(8L, 42L))
            .isInstanceOf(BusinessException.class)
            .hasMessage("模型执行快照不存在");
    }
}
