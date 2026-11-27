package com.prelude.interview.integration;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InterviewStageCatalogTest {

    @Test
    void exposesOneOrderedReadOnlyStageContract() {
        assertThat(InterviewStageCatalog.STAGES)
            .extracting(InterviewStageCatalog.StageDescription::key)
            .containsExactly("warmup", "technical", "deep_dive", "closing");
        assertThat(InterviewStageCatalog.STAGES)
            .extracting(InterviewStageCatalog.StageDescription::wireName)
            .containsExactly("warmup", "technical", "deep_dive", "closing");
        assertThat(InterviewStageCatalog.STAGES)
            .extracting(InterviewStageCatalog.StageDescription::replyQuota)
            .containsExactly(1, 2, 2, 1);
        assertThat(InterviewStageCatalog.STAGES)
            .extracting(InterviewStageCatalog.StageDescription::order)
            .containsExactly(0, 1, 2, 3);
        assertThatThrownBy(() -> InterviewStageCatalog.STAGES.add(null))
            .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void unknownStagesAreExplicit() {
        assertThat(InterviewStageCatalog.find("unknown")).isEmpty();
        assertThatThrownBy(() -> InterviewStageCatalog.require("unknown"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Unknown interview stage");
    }
}
