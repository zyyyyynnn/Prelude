package com.prelude.interview.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.prelude.BusinessException;
import com.prelude.activity.RealtimeConnection;
import com.prelude.activity.RealtimePort;
import com.prelude.activity.SseSessionStream;
import com.prelude.identity.api.SessionValidity;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import org.junit.jupiter.api.Test;

/**
 * The listen channel authorizes the caller and announces the connection; the stream
 * lifecycle stays with the controller that opened it.
 */
class ListenInterviewTest {

    private final InterviewSessionAccess sessionAccess = mock(InterviewSessionAccess.class);
    private final SessionValidity sessionValidity = mock(SessionValidity.class);
    private final RealtimePort realtimePort = mock(RealtimePort.class);
    private final ScheduledExecutorService heartbeatExecutor = mock(ScheduledExecutorService.class);
    private final ListenInterview listenInterview = new ListenInterview(sessionAccess, sessionValidity);

    private final List<String> sent = new ArrayList<>();

    private SseSessionStream openStream() {
        when(heartbeatExecutor.scheduleAtFixedRate(any(Runnable.class), anyLong(), anyLong(), any()))
            .thenAnswer(invocation -> mock(ScheduledFuture.class));
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
        return SseSessionStream.open(realtimePort, 41L, heartbeatExecutor);
    }

    @Test
    void anOwnedSessionAnnouncesTheConnection() {
        when(sessionValidity.isActive("auth-1", 7L)).thenReturn(true);
        when(sessionAccess.currentAccountId()).thenReturn(7L);
        when(sessionAccess.requireOwned(41L, 7L)).thenReturn(null);
        SseSessionStream stream = openStream();

        listenInterview.execute(41L, "auth-1", stream);

        assertThat(sent).containsExactly("ping:connected");
    }

    @Test
    void aRevokedSessionIsRefusedBeforeAnyBusinessFrame() {
        when(sessionValidity.isActive("auth-1", 7L)).thenReturn(false);
        when(sessionAccess.currentAccountId()).thenReturn(7L);
        SseSessionStream stream = openStream();

        assertThatThrownBy(() -> listenInterview.execute(41L, "auth-1", stream))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("登录已失效");
        assertThat(sent).isEmpty();
    }

    @Test
    void anotherAccountsSessionIsRefused() {
        when(sessionValidity.isActive("auth-1", 7L)).thenReturn(true);
        when(sessionAccess.currentAccountId()).thenReturn(7L);
        doThrow(BusinessException.permissionDenied("无权访问"))
            .when(sessionAccess).requireOwned(41L, 7L);
        SseSessionStream stream = openStream();

        assertThatThrownBy(() -> listenInterview.execute(41L, "auth-1", stream))
            .isInstanceOf(BusinessException.class);
        assertThat(sent).isEmpty();
    }
}
