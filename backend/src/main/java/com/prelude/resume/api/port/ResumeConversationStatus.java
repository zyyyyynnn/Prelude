package com.prelude.resume.api.port;

/** Wire values for a resume conversation's place in the sidebar list. The enum is the only
 *  place that spells them; the value is derived from the conversation's turns, never stored. */
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

    public boolean matches(String status) {
        return wire.equals(status);
    }
}
