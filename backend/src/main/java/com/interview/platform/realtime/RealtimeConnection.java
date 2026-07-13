package com.interview.platform.realtime;

public interface RealtimeConnection {

    String connectionId();

    void send(String eventName, Object payload);

    void complete();
}
