package com.prelude.interview.api.port;

/**
 * The session lifecycle wire values. Report and voice consumers compare against these
 * names rather than re-spelling the strings.
 */
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

    public boolean matches(String status) {
        return wire.equals(status);
    }
}
