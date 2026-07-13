package com.interview.platform.realtime;

public interface RealtimePort {

    RealtimeConnection register(Long sessionId, String connectionId, SessionStreamSink sink);

    void unregister(Long sessionId, String connectionId);

    void publish(Long sessionId, String eventName, Object payload);
}
