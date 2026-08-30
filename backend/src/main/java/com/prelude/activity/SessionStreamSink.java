package com.prelude.activity;

public interface SessionStreamSink {

    void send(String eventName, Object payload);

    void complete();
}
