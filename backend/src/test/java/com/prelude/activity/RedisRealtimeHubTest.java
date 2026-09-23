package com.prelude.activity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.listener.adapter.MessageListenerAdapter;
import tools.jackson.databind.ObjectMapper;

/**
 * Redis fan-out hub: local sinks receive their own instance's publish, a remote
 * instance's payload is delivered once, and the originating instance's echo is ignored.
 */
class RedisRealtimeHubTest {

    private static final Long SESSION_ID = 9L;

    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    private final RedisMessageListenerContainer listenerContainer = mock(RedisMessageListenerContainer.class);
    private final RedisRealtimeHub hub =
        new RedisRealtimeHub(redis, listenerContainer, new ObjectMapper());

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
    void publishReachesLocalSinksAndIsBroadcastToOtherInstances() {
        RecordingSink local = new RecordingSink();
        hub.register(SESSION_ID, "c1", local);

        hub.publish(SESSION_ID, "message", "hello");

        assertThat(local.events).containsExactly("message:hello");
        verify(redis).convertAndSend(eq(RedisRealtimeHub.CHANNEL), anyString());
    }

    @Test
    void anEchoFromThisInstanceIsIgnoredSoLocalSinksDoNotSeeTheEventTwice() {
        RecordingSink local = new RecordingSink();
        hub.register(SESSION_ID, "c1", local);

        hub.handleInboundMessage(
            "{\"originInstanceId\":\"" + hub.instanceId() + "\",\"sessionId\":9,\"eventName\":\"message\",\"payload\":\"hello\"}");

        assertThat(local.events).isEmpty();
    }

    @Test
    void anEventFromAnotherInstanceIsDeliveredToTheLocalSinks() {
        RecordingSink local = new RecordingSink();
        hub.register(SESSION_ID, "c1", local);

        hub.handleInboundMessage(
            "{\"originInstanceId\":\"other-instance\",\"sessionId\":9,\"eventName\":\"message\",\"payload\":\"hello\"}");

        assertThat(local.events).containsExactly("message:hello");
    }

    @Test
    void aMalformedBroadcastIsIgnored() {
        RecordingSink local = new RecordingSink();
        hub.register(SESSION_ID, "c1", local);

        hub.handleInboundMessage("{not-json");

        assertThat(local.events).isEmpty();
    }

    @Test
    void connectionCompleteUnregistersSoLaterPublishesSkipTheSink() {
        RecordingSink local = new RecordingSink();
        RealtimeConnection connection = hub.register(SESSION_ID, "c1", local);

        connection.complete();
        hub.publish(SESSION_ID, "message", "after");

        assertThat(local.completes).hasValue(1);
        assertThat(local.events).isEmpty();
    }

    @Test
    void aFailedBroadcastDoesNotDropTheLocalDelivery() {
        when(redis.convertAndSend(anyString(), anyString()))
            .thenThrow(new IllegalStateException("redis down"));
        RecordingSink local = new RecordingSink();
        hub.register(SESSION_ID, "c1", local);

        hub.publish(SESSION_ID, "message", "hello");

        assertThat(local.events).containsExactly("message:hello");
    }

    @Test
    void afterPropertiesSetSubscribesToTheSharedChannel() {
        hub.afterPropertiesSet();

        verify(listenerContainer).addMessageListener(
            eq(hub), any(org.springframework.data.redis.listener.Topic.class));
    }
}
