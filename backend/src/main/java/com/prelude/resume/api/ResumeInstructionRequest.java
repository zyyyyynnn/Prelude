package com.prelude.resume.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * One instruction from the candidate, optionally narrowed to the blocks they selected and
 * carrying the attachments they mounted.
 *
 * <p>Attachments are named, not inlined: the server resolves them against the account, so a
 * request cannot hand over another account's asset id and have it read.
 */
public record ResumeInstructionRequest(
    @NotBlank String instruction,
    List<String> blockIds,
    @Size(max = 5) List<Long> attachmentIds
) {

    public ResumeInstructionRequest {
        blockIds = blockIds == null ? List.of() : List.copyOf(blockIds);
        attachmentIds = attachmentIds == null ? List.of() : List.copyOf(attachmentIds);
    }
}
