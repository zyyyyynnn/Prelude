package com.prelude.resume.domain;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/**
 * One user instruction inside a conversation, and the queue slot it waits in.
 *
 * <p>A turn is submitted {@code queued} and only ever becomes {@code running} by winning
 * the claim, so the states that end a turn — {@code done}, {@code failed},
 * {@code cancelled} — are all reachable from {@code running} and nothing else. A turn
 * that never gets claimed is therefore visible as what it is: queued.
 */
public record ResumeTurn(
    Long id,
    Long conversationId,
    Long accountId,
    String instruction,
    List<String> blockIds,
    List<Long> attachmentIds,
    Status status,
    int queuePosition,
    LocalDateTime createdAt,
    LocalDateTime startedAt,
    LocalDateTime completedAt,
    String failureReason
) {

    public ResumeTurn {
        Objects.requireNonNull(instruction, "instruction");
        blockIds = blockIds == null ? List.of() : List.copyOf(blockIds);
        attachmentIds = attachmentIds == null ? List.of() : List.copyOf(attachmentIds);
    }

    /** The vocabulary every stored turn uses; the wire spelling lives here once. */
    public enum Status {
        QUEUED("queued"),
        RUNNING("running"),
        DONE("done"),
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
            throw new IllegalArgumentException("未知的简历轮次状态: " + value);
        }
    }

    public static ResumeTurn queued(
        Long conversationId,
        Long accountId,
        String instruction,
        List<String> blockIds,
        List<Long> attachmentIds,
        int queuePosition,
        LocalDateTime now
    ) {
        return new ResumeTurn(
            null, conversationId, accountId, instruction, blockIds, attachmentIds,
            Status.QUEUED, queuePosition, now, null, null, null);
    }

    public ResumeTurn withId(Long newId) {
        return new ResumeTurn(newId, conversationId, accountId, instruction, blockIds,
            attachmentIds, status, queuePosition, createdAt, startedAt, completedAt, failureReason);
    }

    public boolean isTerminal() {
        return status == Status.DONE || status == Status.FAILED || status == Status.CANCELLED;
    }
}
