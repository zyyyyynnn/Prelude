package com.prelude.llm;

import com.prelude.BusinessException;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ModelExecutionParametersTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void missingProfileValueResolvesToConcreteDefaultBeforeFreeze() {
        ModelExecutionParameters parameters = ModelExecutionParameters.fromProfileJson(null, objectMapper);

        assertThat(parameters.maxOutputTokens()).isEqualTo(4096);
        assertThat(parameters.toJson(objectMapper)).isEqualTo("{\"maxOutputTokens\":4096}");
    }

    @Test
    void outputBudgetRejectsValuesOutsideTheGovernedRange() {
        assertThatThrownBy(() -> ModelExecutionParameters.resolve(0))
            .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> ModelExecutionParameters.resolve(32769))
            .isInstanceOf(BusinessException.class);
    }

    @Test
    void frozenExecutionParametersCannotFallBackToMutableDefaults() {
        assertThatThrownBy(() -> ModelExecutionParameters.fromFrozenJson("{}", objectMapper))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("Frozen model execution parameters are incomplete");
    }
}
