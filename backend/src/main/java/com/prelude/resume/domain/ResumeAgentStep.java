package com.prelude.resume.domain;

import java.util.List;

/**
 * One action inside a run — the trace the workspace replays, and the only thing the product
 * shows about how a patch was produced.
 *
 * <p>A step whose {@code toolName} is set was invoked by the model; the handler recorded it
 * when it ran. Steps with no tool name are the run's own fixed work, and a failed step keeps
 * its own error text: the run can still finish, so the failure belongs to the row.
 */
public record ResumeAgentStep(
    Long id,
    Long runId,
    int sortOrder,
    Kind kind,
    String label,
    String toolName,
    String argumentsJson,
    String resultExcerpt,
    List<FileDiff> files,
    List<String> chips,
    List<String> detail,
    String badge,
    BadgeTone badgeTone,
    State state,
    String error
) {

    public ResumeAgentStep {
        files = files == null ? List.of() : List.copyOf(files);
        chips = chips == null ? List.of() : List.copyOf(chips);
        detail = detail == null ? List.of() : List.copyOf(detail);
        /* The column is NOT NULL DEFAULT '', so a step that was not a tool call comes back as
           an empty string. Reading that as "the model called a tool" would inflate the run's
           own count. */
        toolName = toolName == null || toolName.isBlank() ? null : toolName;
    }

    /** Which glyph and grouping the workspace row uses; owned here, mirrored by the UI. */
    public enum Kind {
        THINK("think"),
        READ("read"),
        SEARCH("search"),
        WRITE("write"),
        RUN("run"),
        POLICY("policy"),
        PROPOSAL("proposal");

        private final String wire;

        Kind(String wire) {
            this.wire = wire;
        }

        public String wire() {
            return wire;
        }

        public static Kind fromWire(String value) {
            for (Kind candidate : values()) {
                if (candidate.wire.equals(value)) {
                    return candidate;
                }
            }
            throw new IllegalArgumentException("未知的简历步骤类型: " + value);
        }
    }

    public enum State {
        RUNNING("running"),
        DONE("done"),
        ERROR("error");

        private final String wire;

        State(String wire) {
            this.wire = wire;
        }

        public String wire() {
            return wire;
        }

        public static State fromWire(String value) {
            for (State candidate : values()) {
                if (candidate.wire.equals(value)) {
                    return candidate;
                }
            }
            throw new IllegalArgumentException("未知的简历步骤状态: " + value);
        }
    }

    public enum BadgeTone {
        DEFAULT("default"),
        ADD("add"),
        ERROR("error");

        private final String wire;

        BadgeTone(String wire) {
            this.wire = wire;
        }

        public String wire() {
            return wire;
        }

        public static BadgeTone fromWire(String value) {
            for (BadgeTone candidate : values()) {
                if (candidate.wire.equals(value)) {
                    return candidate;
                }
            }
            throw new IllegalArgumentException("未知的简历步骤徽标色位: " + value);
        }
    }

    /** A file a step wrote, with the line counts measured from the edit itself. */
    public record FileDiff(String name, int add, int del) {
    }
}
