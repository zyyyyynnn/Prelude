package com.interview.interview.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InterviewStagePolicyTest {

    private final InterviewStagePolicy policy = new InterviewStagePolicy();

    @Test
    void allowsOnlyTheNextStageAndNeverMovesBackward() {
        assertThat(policy.requireForwardTransition("warmup", "technical")).isEqualTo("technical");
        assertThatThrownBy(() -> policy.requireForwardTransition("technical", "warmup"))
            .isInstanceOf(StageTransitionException.class)
            .hasMessageContaining("不可回退");
        assertThatThrownBy(() -> policy.requireForwardTransition("warmup", "deep_dive"))
            .isInstanceOf(StageTransitionException.class)
            .hasMessageContaining("顺序不正确");
    }

    @Test
    void normalizesKnownStageAndRejectsInvalidValues() {
        assertThat(policy.normalize(" technical ")).isEqualTo("technical");
        assertThatThrownBy(() -> policy.normalize("unknown"))
            .isInstanceOf(StageTransitionException.class)
            .hasMessageContaining("无效");
        assertThatThrownBy(() -> policy.normalize(" "))
            .isInstanceOf(StageTransitionException.class)
            .hasMessageContaining("不能为空");
    }

    @Test
    void resolvesNextStageAndFinalStage() {
        assertThat(policy.nextAfter("deep_dive")).contains("closing");
        assertThat(policy.nextAfter("closing")).isEmpty();
        assertThat(policy.nextAfter("legacy-stage")).contains("technical");
    }
}
