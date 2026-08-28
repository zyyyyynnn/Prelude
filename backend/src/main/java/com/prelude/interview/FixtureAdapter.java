package com.prelude.interview;

import com.prelude.interview.application.port.InterviewFixturePort;
import java.util.function.Consumer;
import org.springframework.stereotype.Component;

@Component("interviewFixtureAdapter")
class FixtureAdapter implements InterviewFixturePort {
    @Override public boolean isEnabled() { return false; }
    @Override public String resolveMockJudge(String stageName, int replyIndex) { throw new UnsupportedOperationException(); }
    @Override public String resolveScriptedReply(String stageName, int replyIndex) { throw new UnsupportedOperationException(); }
    @Override public void streamReply(String reply, Consumer<String> consumer) { throw new UnsupportedOperationException(); }
}
