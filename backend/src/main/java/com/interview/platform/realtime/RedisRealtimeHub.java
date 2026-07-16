package com.interview.platform.realtime;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Slf4j
@Component
@ConditionalOnProperty(prefix = "prelude.realtime", name = "mode", havingValue = "redis")
public class RedisRealtimeHub implements RealtimePort, MessageListener, InitializingBean {

    static final String CHANNEL = "prelude:realtime:events";

    private final String instanceId = UUID.randomUUID().toString();
    private final RealtimeConnectionRegistry registry = new RealtimeConnectionRegistry();
    private final StringRedisTemplate stringRedisTemplate;
    private final RedisMessageListenerContainer listenerContainer;
    private final ObjectMapper objectMapper;

    public RedisRealtimeHub(
        StringRedisTemplate stringRedisTemplate,
        RedisMessageListenerContainer listenerContainer,
        ObjectMapper objectMapper
    ) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.listenerContainer = listenerContainer;
        this.objectMapper = objectMapper;
    }

    @Override
    public void afterPropertiesSet() {
        listenerContainer.addMessageListener(this, new ChannelTopic(CHANNEL));
    }

    @Override
    public RealtimeConnection register(Long sessionId, String connectionId, SessionStreamSink sink) {
        registry.register(sessionId, connectionId, sink);
        return new RedisConnection(sessionId, connectionId, sink);
    }

    @Override
    public void unregister(Long sessionId, String connectionId) {
        registry.unregister(sessionId, connectionId);
    }

    @Override
    public void publish(Long sessionId, String eventName, Object payload) {
        registry.publish(sessionId, eventName, payload);
        try {
            stringRedisTemplate.convertAndSend(
                CHANNEL,
                objectMapper.writeValueAsString(new RealtimeEventMessage(
                    instanceId,
                    sessionId,
                    eventName,
                    serializePayload(payload)
                ))
            );
        } catch (JsonProcessingException error) {
            log.warn("Failed to broadcast realtime event '{}' for session {}", eventName, sessionId, error);
        }
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        String body = new String(message.getBody(), StandardCharsets.UTF_8);
        RealtimeEventMessage event;
        try {
            event = objectMapper.readValue(body, RealtimeEventMessage.class);
        } catch (JsonProcessingException error) {
            log.warn("Ignored malformed realtime pub/sub payload", error);
            return;
        }
        if (instanceId.equals(event.originInstanceId())) {
            return;
        }
        registry.publish(event.sessionId(), event.eventName(), deserializePayload(event.payload()));
    }

    void handleInboundMessage(String body) {
        onMessage(new Message() {
            @Override
            public byte[] getBody() {
                return body.getBytes(StandardCharsets.UTF_8);
            }

            @Override
            public byte[] getChannel() {
                return CHANNEL.getBytes(StandardCharsets.UTF_8);
            }
        }, CHANNEL.getBytes(StandardCharsets.UTF_8));
    }

    String instanceId() {
        return instanceId;
    }

    private String serializePayload(Object payload) {
        if (payload == null) {
            return "";
        }
        if (payload instanceof String text) {
            return text;
        }
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException error) {
            throw new IllegalArgumentException("Realtime payload must be serializable", error);
        }
    }

    private Object deserializePayload(String payload) {
        return payload == null ? "" : payload;
    }

    private record RealtimeEventMessage(
        String originInstanceId,
        Long sessionId,
        String eventName,
        String payload
    ) {
    }

    private final class RedisConnection implements RealtimeConnection {
        private final Long sessionId;
        private final String connectionId;
        private final SessionStreamSink sink;

        private RedisConnection(Long sessionId, String connectionId, SessionStreamSink sink) {
            this.sessionId = sessionId;
            this.connectionId = connectionId;
            this.sink = sink;
        }

        @Override
        public String connectionId() {
            return connectionId;
        }

        @Override
        public void send(String eventName, Object payload) {
            sink.send(eventName, payload);
        }

        @Override
        public void complete() {
            try {
                sink.complete();
            } finally {
                unregister(sessionId, connectionId);
            }
        }
    }
}
