package com.interview.platform.realtime;

import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class LocalRealtimeHubTest {

    private final LocalRealtimeHub hub = new LocalRealtimeHub();

    @Test
    void publishesToEveryConnectionRegisteredForSession() {
        SessionStreamSink first = mock(SessionStreamSink.class);
        SessionStreamSink second = mock(SessionStreamSink.class);
        hub.register(7L, "first", first);
        hub.register(7L, "second", second);

        hub.publish(7L, "report_ready", "done");

        verify(first).send("report_ready", "done");
        verify(second).send("report_ready", "done");
    }

    @Test
    void removesConnectionAfterDeliveryFailure() {
        SessionStreamSink sink = mock(SessionStreamSink.class);
        doThrow(new RuntimeException("closed")).when(sink).send("event", "first");
        hub.register(7L, "connection", sink);

        hub.publish(7L, "event", "first");
        hub.publish(7L, "event", "second");

        verify(sink, times(1)).send("event", "first");
        verify(sink, never()).send("event", "second");
    }

    @Test
    void completingConnectionUnregistersIt() {
        SessionStreamSink sink = mock(SessionStreamSink.class);
        RealtimeConnection connection = hub.register(7L, "connection", sink);

        connection.complete();
        hub.publish(7L, "event", "data");

        verify(sink).complete();
        verify(sink, never()).send("event", "data");
    }

    @Test
    void publishWithoutConnectionDoesNotThrow() {
        hub.publish(99L, "report_ready", "done");
    }
}
