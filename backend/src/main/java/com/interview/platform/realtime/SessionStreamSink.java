package com.interview.platform.realtime;

public interface SessionStreamSink {

    void send(String eventName, Object payload);

    void complete();
}
