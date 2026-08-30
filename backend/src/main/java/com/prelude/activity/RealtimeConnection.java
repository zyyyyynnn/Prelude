package com.prelude.activity;

public interface RealtimeConnection {

    String connectionId();

    void send(String eventName, Object payload);

    void complete();
}
