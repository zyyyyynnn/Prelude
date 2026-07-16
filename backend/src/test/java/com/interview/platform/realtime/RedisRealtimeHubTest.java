package com.interview.platform.realtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RedisRealtimeHubTest {

    @Mock
    private StringRedisTemplate stringRedisTemplate;
    @Mock
    private RedisMessageListenerContainer listenerContainer;

    private RedisRealtimeHub hub;

    @BeforeEach
    void setUp() {
        hub = new RedisRealtimeHub(stringRedisTemplate, listenerContainer, new ObjectMapper());
    }

    @Test
    void publishDeliversLocallyAndBroadcastsSerializedEvent() {
        SessionStreamSink sink = mock(SessionStreamSink.class);
        hub.register(7L, "connection", sink);
        when(stringRedisTemplate.convertAndSend(anyString(), anyString())).thenReturn(1L);

        hub.publish(7L, "report_ready", "done");

        verify(sink).send("report_ready", "done");
        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
        verify(stringRedisTemplate).convertAndSend(eq(RedisRealtimeHub.CHANNEL), payloadCaptor.capture());
        assertThat(payloadCaptor.getValue())
            .contains("\"sessionId\":7")
            .contains("\"eventName\":\"report_ready\"")
            .contains("\"payload\":\"done\"");
    }

    @Test
    void inboundMessageFromPeerInstanceFanoutsLocally() {
        SessionStreamSink sink = mock(SessionStreamSink.class);
        hub.register(7L, "connection", sink);

        hub.handleInboundMessage(
            "{\"originInstanceId\":\"peer-instance\",\"sessionId\":7,\"eventName\":\"report_ready\",\"payload\":\"done\"}"
        );

        verify(sink).send("report_ready", "done");
        verify(stringRedisTemplate, never()).convertAndSend(anyString(), anyString());
    }

    @Test
    void ignoresInboundMessageOriginatingFromSameInstance() {
        SessionStreamSink sink = mock(SessionStreamSink.class);
        hub.register(7L, "connection", sink);

        hub.handleInboundMessage(String.format(
            Locale.ROOT,
            "{\"originInstanceId\":\"%s\",\"sessionId\":7,\"eventName\":\"report_ready\",\"payload\":\"done\"}",
            hub.instanceId()
        ));

        verify(sink, never()).send(anyString(), anyString());
    }
}
