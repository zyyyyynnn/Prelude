package com.prelude.activity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * The SSE connection schedules one heartbeat and cancels it when the stream ends, so an
 * idle proxy keeps the turn alive without leaking a timer after close.
 */
class SseSessionStreamTest {

    private final RealtimePort realtimePort = mock(RealtimePort.class);
    private final ScheduledExecutorService heartbeatExecutor = mock(ScheduledExecutorService.class);
    private final ScheduledFuture<?> heartbeat = mock(ScheduledFuture.class);
    private final List<String> sent = new ArrayList<>();

    private SseSessionStream open() {
        when(heartbeatExecutor.scheduleAtFixedRate(any(Runnable.class), anyLong(), anyLong(), any()))
            .thenAnswer(invocation -> heartbeat);
        RealtimeConnection connection = new RealtimeConnection() {
            @Override
            public String connectionId() {
                return "c1";
            }

            @Override
            public void send(String eventName, Object payload) {
                sent.add(eventName + ":" + payload);
            }

            @Override
            public void complete() {
                sent.add("complete");
            }
        };
        when(realtimePort.register(anyLong(), anyString(), any())).thenReturn(connection);
        return SseSessionStream.open(realtimePort, 7L, heartbeatExecutor);
    }

    @Test
    void openRegistersTheConnectionAndSchedulesAHeartbeat() {
        open();

        verify(realtimePort).register(eq(7L), anyString(), any());
        verify(heartbeatExecutor).scheduleAtFixedRate(
            any(Runnable.class), eq(30_000L), eq(30_000L), eq(TimeUnit.MILLISECONDS));
    }

    @Test
    void theHeartbeatTaskPingsTheConnection() {
        open();
        ArgumentCaptor<Runnable> task = ArgumentCaptor.forClass(Runnable.class);
        verify(heartbeatExecutor).scheduleAtFixedRate(
            task.capture(), anyLong(), anyLong(), any());

        task.getValue().run();

        assertThat(sent).containsExactly("ping:heartbeat");
    }

    @Test
    void completeCancelsTheHeartbeatAndClosesTheConnection() {
        SseSessionStream stream = open();

        stream.complete();

        verify(heartbeat).cancel(false);
        assertThat(sent).containsExactly("complete");
    }

    @Test
    void completeWithErrorSendsTheReasonThenCloses() {
        SseSessionStream stream = open();

        stream.completeWithError("连接超时，请重试");

        assertThat(sent).containsExactly("error:连接超时，请重试", "complete");
        verify(heartbeat).cancel(false);
    }

    @Test
    void emitterIsTheCallersTransportHandle() {
        SseSessionStream stream = open();

        assertThat(stream.emitter()).isInstanceOf(SseEmitter.class);
    }
}
