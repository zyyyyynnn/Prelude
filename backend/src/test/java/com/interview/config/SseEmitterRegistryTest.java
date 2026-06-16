package com.interview.config;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.function.Consumer;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class SseEmitterRegistryTest {

    private final SseEmitterRegistry registry = new SseEmitterRegistry();

    @Test
    void broadcastSendsEventToRegisteredEmitter() throws Exception {
        SseEmitter emitter = mock(SseEmitter.class);
        registry.register(7L, emitter);

        registry.broadcast(7L, "report_ready", "done");

        verify(emitter).send(any(SseEmitter.SseEventBuilder.class));
    }

    @Test
    void broadcastRemovesEmitterWhenSendFails() throws Exception {
        SseEmitter emitter = mock(SseEmitter.class);
        doThrow(new IOException("closed")).when(emitter).send(any(SseEmitter.SseEventBuilder.class));
        registry.register(7L, emitter);

        registry.broadcast(7L, "report_ready", "done");
        registry.broadcast(7L, "report_ready", "again");

        verify(emitter).send(any(SseEmitter.SseEventBuilder.class));
    }

    @Test
    void completionTimeoutAndErrorCallbacksRemoveEmitter() throws Exception {
        SseEmitter completionEmitter = mock(SseEmitter.class);
        ArgumentCaptor<Runnable> completion = ArgumentCaptor.forClass(Runnable.class);
        registry.register(1L, completionEmitter);
        verify(completionEmitter).onCompletion(completion.capture());
        completion.getValue().run();
        registry.broadcast(1L, "event", "data");
        verify(completionEmitter, never()).send(any(SseEmitter.SseEventBuilder.class));

        SseEmitter timeoutEmitter = mock(SseEmitter.class);
        ArgumentCaptor<Runnable> timeout = ArgumentCaptor.forClass(Runnable.class);
        registry.register(2L, timeoutEmitter);
        verify(timeoutEmitter).onTimeout(timeout.capture());
        timeout.getValue().run();
        registry.broadcast(2L, "event", "data");
        verify(timeoutEmitter, never()).send(any(SseEmitter.SseEventBuilder.class));

        SseEmitter errorEmitter = mock(SseEmitter.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Consumer<Throwable>> error = ArgumentCaptor.forClass(Consumer.class);
        registry.register(3L, errorEmitter);
        verify(errorEmitter).onError(error.capture());
        error.getValue().accept(new IOException("closed"));
        registry.broadcast(3L, "event", "data");
        verify(errorEmitter, never()).send(any(SseEmitter.SseEventBuilder.class));
    }

    @Test
    void broadcastWithoutEmitterDoesNotThrow() {
        registry.broadcast(99L, "report_ready", "done");
    }
}
