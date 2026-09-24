package com.prelude.interview.application;

import com.prelude.interview.domain.InterviewSession;

/**
 * Ownership and lifecycle checks for one interview session, shared by the interview
 * use cases. The domain model stays inside the module; this port hands out the session
 * those use cases are allowed to mutate.
 */
public interface InterviewSessionAccess {

    long currentAccountId();

    InterviewSession requireOwned(Long sessionId, long accountId);

    InterviewSession requireOngoing(Long sessionId, long accountId);
}
