package com.prelude.interview.application.port;

import com.prelude.interview.domain.InterviewSession;

import java.util.List;
import java.util.Map;

/**
 * Context-window assembly for interview turns. Keeps use cases off the
 * concrete context service so package depth stays shallow.
 */
public interface InterviewContextPort {

    List<Map<String, String>> buildContextMessages(Long sessionId);

    List<Map<String, String>> buildAutoStartMessages(InterviewSession session);
}
