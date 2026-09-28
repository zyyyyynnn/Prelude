package com.prelude.resume.api.port;

/** Wire values for a resume turn. The enum is the only place that spells them. */
public enum ResumeTurnStatus {

    QUEUED("queued"),
    RUNNING("running"),
    DONE("done");

    private final String wire;

    ResumeTurnStatus(String wire) {
        this.wire = wire;
    }

    public String wire() {
        return wire;
    }

    public boolean matches(String status) {
        return wire.equals(status);
    }
}
