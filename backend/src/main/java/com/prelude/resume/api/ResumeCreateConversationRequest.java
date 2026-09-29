package com.prelude.resume.api;

/**
 * Opening a resume conversation.
 *
 * <p>The resume is optional because the workspace that opens one has not chosen a document yet;
 * a run then resolves it, and refuses rather than guessing when the account holds more than one.
 */
public record ResumeCreateConversationRequest(Long resumeId) {
}
