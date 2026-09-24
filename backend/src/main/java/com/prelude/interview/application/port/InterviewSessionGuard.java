package com.prelude.interview.application.port;

/**
 * The only question another module may ask about a session: does this account own an ongoing
 * one with that id. Voice previously reached through {@code InterviewSessionRepository}, which
 * also handed it the ability to delete a session or list a user's whole history.
 */
public interface InterviewSessionGuard {

    boolean isOngoing(Long accountId, Long sessionId);
}
