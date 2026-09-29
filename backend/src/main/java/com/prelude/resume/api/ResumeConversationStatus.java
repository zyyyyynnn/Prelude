package com.prelude.resume.api;

/**
 * A conversation's place in the sidebar. Derived from its turns and never stored: a
 * conversation is active while any turn is queued or running, and finished once every turn
 * has reached a terminal state — including failed and cancelled.
 */
public enum ResumeConversationStatus {

    ACTIVE("active"),
    FINISHED("finished");

    private final String wire;

    ResumeConversationStatus(String wire) {
        this.wire = wire;
    }

    public String wire() {
        return wire;
    }
}
