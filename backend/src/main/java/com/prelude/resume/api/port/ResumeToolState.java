package com.prelude.resume.api.port;

/** Wire values for one tool call. Failures stay on the item, never on the group. */
public enum ResumeToolState {

    PENDING("pending"),
    RUNNING("running"),
    DONE("done"),
    ERROR("error");

    private final String wire;

    ResumeToolState(String wire) {
        this.wire = wire;
    }

    public String wire() {
        return wire;
    }

    public boolean matches(String state) {
        return wire.equals(state);
    }
}
