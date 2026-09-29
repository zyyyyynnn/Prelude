package com.prelude.resume.api;

import java.util.List;

/**
 * The resume document at its current revision — what the workspace previews, and the set of
 * blocks a candidate can select an instruction against.
 */
public record ResumeDocumentResponse(
    Long resumeId,
    int revisionNumber,
    List<Block> blocks
) {

    /** A block addressed by the stable id a patch names. */
    public record Block(String id, String section, String kind, String text) {
    }
}
