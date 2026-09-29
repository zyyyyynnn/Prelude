package com.prelude.resume.api;

import java.util.List;

/**
 * One step of a run as the workspace trace renders it.
 *
 * <p>{@code state} and {@code files} are part of the shape the stream already consumes: a
 * failed step keeps its own row red while the run still closes, and the file a step wrote is
 * reported as measured line counts rather than folded into the row's words.
 */
public record ResumeToolStepResponse(
    Long id,
    String icon,
    String text,
    String badge,
    String badgeTone,
    List<String> chips,
    List<String> detail,
    List<FileDiff> files,
    String state,
    String error
) {

    /** A file the step wrote, with the lines it added and removed. */
    public record FileDiff(String name, int add, int del) {
    }
}
