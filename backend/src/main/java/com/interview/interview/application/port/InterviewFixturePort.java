package com.interview.interview.application.port;

import java.util.function.Consumer;

public interface InterviewFixturePort {

    boolean isEnabled();

    String resolveMockJudge(String stageName, int replyIndex);

    String resolveScriptedReply(String stageName, int replyIndex);

    void streamReply(String reply, Consumer<String> consumer);
}
