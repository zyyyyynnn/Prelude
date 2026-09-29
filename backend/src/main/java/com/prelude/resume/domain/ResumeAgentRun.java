package com.prelude.resume.domain;

import java.time.LocalDateTime;

/**
 * One assistant run for one instruction turn, and the exact inputs it was frozen against.
 *
 * <p>The frozen model snapshot and base revision are recorded here rather than read at
 * decision time: a run must keep executing on the configuration it started with even if
 * the account changes its model, and a patch must be judged against the revision it was
 * written on.
 */
public record ResumeAgentRun(
    Long id,
    Long turnId,
    Long conversationId,
    Long resumeId,
    int baseRevision,
    Long modelExecutionSnapshotId,
    String promptId,
    Status status,
    String failureReason,
    LocalDateTime startedAt,
    LocalDateTime finishedAt
) {

    public enum Status {
        RUNNING("running"),
        PROPOSAL_READY("proposal_ready"),
        INVALID("invalid"),
        FAILED("failed"),
        CANCELLED("cancelled");

        private final String wire;

        Status(String wire) {
            this.wire = wire;
        }

        public String wire() {
            return wire;
        }

        public static Status fromWire(String value) {
            for (Status candidate : values()) {
                if (candidate.wire.equals(value)) {
                    return candidate;
                }
            }
            throw new IllegalArgumentException("未知的简历运行状态: " + value);
        }
    }

    public ResumeAgentRun withId(Long newId) {
        return new ResumeAgentRun(newId, turnId, conversationId, resumeId, baseRevision,
            modelExecutionSnapshotId, promptId, status, failureReason, startedAt, finishedAt);
    }
}
