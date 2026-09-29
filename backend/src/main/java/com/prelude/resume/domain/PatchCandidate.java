package com.prelude.resume.domain;

import java.util.List;

/**
 * What the model is asked to return: a stated reason, block-level operations, and the
 * claims it is introducing.
 *
 * <p>This is the only shape a model may answer in on this path, and it is never shown to a
 * candidate as-is — the workspace reads a {@link ResumePatchProposal} that a policy has
 * already checked against the document.
 */
public record PatchCandidate(String reason, List<Operation> operations) {

    public PatchCandidate {
        operations = operations == null ? List.of() : List.copyOf(operations);
    }

    /**
     * One requested change. {@code op} is the model's own spelling — replace, insert or
     * delete — and is validated rather than trusted, because a wrong verb would otherwise
     * silently rewrite a block the candidate never meant to touch.
     */
    public record Operation(String op, String blockId, String section, String text) {
    }

    public static final int MAX_OPERATIONS = 20;

    public static final int MAX_TEXT_LENGTH = 4000;
}
