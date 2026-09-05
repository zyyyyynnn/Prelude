package com.prelude.llm.api;

/**
 * Reference to an immutable frozen model execution configuration. Runs hold
 * this reference; they never re-read the mutable profile.
 */
public record ModelExecutionSnapshotRef(Long snapshotId) {
}
