package com.prelude.interview.domain;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InterviewStagePolicyTest {

    private final InterviewStagePolicy policy = new InterviewStagePolicy();

    @Test
    void normalizeRejectsBlankAndUnknownStageNames() {
        assertThatThrownBy(() -> policy.normalize(null))
            .isInstanceOf(StageTransitionException.class)
            .hasMessage("stageName 不能为空");
        assertThatThrownBy(() -> policy.normalize("  "))
            .isInstanceOf(StageTransitionException.class)
            .hasMessage("stageName 不能为空");
        assertThatThrownBy(() -> policy.normalize("unknown"))
            .isInstanceOf(StageTransitionException.class)
            .hasMessage("无效的面试阶段");
    }

    @Test
    void normalizeTrimsKnownStageNames() {
        assertThat(policy.normalize(" technical ")).isEqualTo("technical");
    }

    @Test
    void requireForwardTransitionAcceptsOnlyTheImmediateNextStage() {
        assertThat(policy.requireForwardTransition("warmup", "technical")).isEqualTo("technical");
        assertThat(policy.requireForwardTransition("technical", "deep_dive")).isEqualTo("deep_dive");
        assertThat(policy.requireForwardTransition("deep_dive", "closing")).isEqualTo("closing");
    }

    @Test
    void requireForwardTransitionRejectsBackwardAndSkippedJumps() {
        assertThatThrownBy(() -> policy.requireForwardTransition("technical", "warmup"))
            .isInstanceOf(StageTransitionException.class)
            .hasMessage("面试阶段不可回退");
        assertThatThrownBy(() -> policy.requireForwardTransition("warmup", "deep_dive"))
            .isInstanceOf(StageTransitionException.class)
            .hasMessage("阶段推进顺序不正确");
        assertThatThrownBy(() -> policy.requireForwardTransition("warmup", "closing"))
            .isInstanceOf(StageTransitionException.class)
            .hasMessage("阶段推进顺序不正确");
    }

    @Test
    void nextAfterWalksTheOrderedStagesAndStopsAfterClosing() {
        assertThat(policy.nextAfter("warmup")).contains("technical");
        assertThat(policy.nextAfter("technical")).contains("deep_dive");
        assertThat(policy.nextAfter("deep_dive")).contains("closing");
        assertThat(policy.nextAfter("closing")).isEqualTo(Optional.empty());
        assertThat(policy.nextAfter("unknown")).contains("technical");
    }
}
