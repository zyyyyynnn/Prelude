package com.prelude.interview.domain;

import com.prelude.interview.integration.InterviewSessionStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InterviewSessionStatusTest {

    @Test
    void permitsOnlyTheDeclaredLifecycleTransitions() {
        assertThat(InterviewSessionStatus.ONGOING.startGenerating()).isEqualTo(InterviewSessionStatus.GENERATING);
        assertThat(InterviewSessionStatus.GENERATING.finishGenerating()).isEqualTo(InterviewSessionStatus.FINISHED);
        assertThat(InterviewSessionStatus.GENERATING.restoreOngoing()).isEqualTo(InterviewSessionStatus.ONGOING);

        assertThatThrownBy(InterviewSessionStatus.FINISHED::startGenerating)
            .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(InterviewSessionStatus.GENERATING::startGenerating)
            .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(InterviewSessionStatus.ONGOING::finishGenerating)
            .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(InterviewSessionStatus.FINISHED::restoreOngoing)
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void keepsWireNamesAndRejectsUnknownPersistedValues() {
        assertThat(InterviewSessionStatus.fromWire("ongoing")).isEqualTo(InterviewSessionStatus.ONGOING);
        assertThat(InterviewSessionStatus.fromWire("generating")).isEqualTo(InterviewSessionStatus.GENERATING);
        assertThat(InterviewSessionStatus.fromWire("finished")).isEqualTo(InterviewSessionStatus.FINISHED);
        assertThatThrownBy(() -> InterviewSessionStatus.fromWire("unknown"))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
