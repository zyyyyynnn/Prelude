package com.prelude.interview.application.port;

public record JudgeOutcome(Status status, JudgeResult result, String error) {

    public enum Status {
        COMPLETED,
        BUSY,
        UNSCORED
    }

    public static JudgeOutcome completed(JudgeResult result) {
        return new JudgeOutcome(Status.COMPLETED, result, null);
    }

    public static JudgeOutcome busy() {
        return new JudgeOutcome(Status.BUSY, null, null);
    }

    public static JudgeOutcome unscored(String error) {
        return new JudgeOutcome(Status.UNSCORED, null, error);
    }
}
