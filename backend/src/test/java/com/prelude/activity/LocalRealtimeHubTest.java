package com.prelude.activity;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Local realtime hub: multi-connection fan-out, completion cleanup, and
 * removal of sinks that fail during publish.
 */
class LocalRealtimeHubTest {

    private static final Long SESSION_ID = 77L;

    private final LocalRealtimeHub hub = new LocalRealtimeHub();

    private static final class RecordingSink implements SessionStreamSink {
        private final List<String> events = new ArrayList<>();
        private final AtomicInteger completes = new AtomicInteger();

        @Override
        public void send(String eventName, Object payload) {
            events.add(eventName + ":" + payload);
        }

        @Override
        public void complete() {
            completes.incrementAndGet();
        }
    }

    @Test
    void publishFansOutToEveryRegisteredConnectionForTheSession() {
        RecordingSink first = new RecordingSink();
        RecordingSink second = new RecordingSink();
        hub.register(SESSION_ID, "c1", first);
        hub.register(SESSION_ID, "c2", second);
        hub.register(SESSION_ID + 1, "other", new RecordingSink());

        hub.publish(SESSION_ID, "message", "hello");

        assertThat(first.events).containsExactly("message:hello");
        assertThat(second.events).containsExactly("message:hello");
    }

    @Test
    void publishWithoutConnectionsIsANoOp() {
        hub.publish(SESSION_ID, "message", "hello");
    }

    @Test
    void connectionSendAndCompleteReachTheSinkAndUnregister() {
        RecordingSink sink = new RecordingSink();
        RealtimeConnection connection = hub.register(SESSION_ID, "c1", sink);

        connection.send("message", "delta");
        connection.complete();

        assertThat(connection.connectionId()).isEqualTo("c1");
        assertThat(sink.events).containsExactly("message:delta");
        assertThat(sink.completes).hasValue(1);

        hub.publish(SESSION_ID, "message", "after-complete");
        assertThat(sink.events).containsExactly("message:delta");
    }

    @Test
    void unregisterRemovesOnlyTheTargetConnection() {
        RecordingSink first = new RecordingSink();
        RecordingSink second = new RecordingSink();
        hub.register(SESSION_ID, "c1", first);
        hub.register(SESSION_ID, "c2", second);

        hub.unregister(SESSION_ID, "c1");
        hub.publish(SESSION_ID, "message", "hello");

        assertThat(first.events).isEmpty();
        assertThat(second.events).containsExactly("message:hello");
    }

    @Test
    void publishDropsConnectionsWhoseSinkThrows() {
        SessionStreamSink failing = mock(SessionStreamSink.class);
        doThrow(new IllegalStateException("sink down")).when(failing).send("message", "hello");
        RecordingSink healthy = new RecordingSink();
        hub.register(SESSION_ID, "bad", failing);
        hub.register(SESSION_ID, "good", healthy);

        hub.publish(SESSION_ID, "message", "hello");
        hub.publish(SESSION_ID, "message", "again");

        verify(failing, times(1)).send("message", "hello");
        verify(failing, never()).send("message", "again");
        assertThat(healthy.events).containsExactly("message:hello", "message:again");
    }
}
