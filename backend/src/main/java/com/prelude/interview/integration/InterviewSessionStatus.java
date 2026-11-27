package com.prelude.interview.integration;

public enum InterviewSessionStatus {
    ONGOING("ongoing"),
    GENERATING("generating"),
    FINISHED("finished");

    private final String wire;

    InterviewSessionStatus(String wire) {
        this.wire = wire;
    }

    public String wire() {
        return wire;
    }

    public static InterviewSessionStatus fromWire(String wire) {
        for (InterviewSessionStatus status : values()) {
            if (status.wire.equals(wire)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown interview session status: " + wire);
    }

    public InterviewSessionStatus startGenerating() {
        return transitionTo(GENERATING);
    }

    public InterviewSessionStatus finishGenerating() {
        return transitionTo(FINISHED);
    }

    public InterviewSessionStatus restoreOngoing() {
        return transitionTo(ONGOING);
    }

    private InterviewSessionStatus transitionTo(InterviewSessionStatus target) {
        boolean allowed = this == ONGOING && target == GENERATING
            || this == GENERATING && (target == FINISHED || target == ONGOING);
        if (!allowed) {
            throw new IllegalStateException("Invalid interview session transition: " + wire + " -> " + target.wire);
        }
        return target;
    }
}
