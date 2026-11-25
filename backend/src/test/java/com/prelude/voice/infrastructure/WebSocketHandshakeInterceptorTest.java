package com.prelude.voice.infrastructure;

import com.prelude.identity.api.security.OriginPolicy;
import com.prelude.identity.infrastructure.security.CorsProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.web.socket.WebSocketHandler;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class WebSocketHandshakeInterceptorTest {

    private static final String FRONTEND = "https://frontend.example";
    private AnnotationConfigApplicationContext context;
    private WebSocketHandshakeInterceptor interceptor;

    @BeforeEach
    void createInterceptor() {
        context = new AnnotationConfigApplicationContext();
        context.registerBean(CorsProperties.class, () -> new CorsProperties(List.of(FRONTEND)));
        context.scan("com.prelude.identity.infrastructure.security");
        context.refresh();
        interceptor = new WebSocketHandshakeInterceptor(context.getBean(OriginPolicy.class));
    }

    @AfterEach
    void closeContext() {
        context.close();
    }

    @Test
    void acceptsAuthenticatedSameOriginAndConfiguredCrossOriginHandshakes() throws Exception {
        Map<String, Object> sameAttributes = new HashMap<>();
        assertThat(interceptor.beforeHandshake(
            request("https://app.example"), mock(ServerHttpResponse.class), mock(WebSocketHandler.class), sameAttributes))
            .isTrue();
        assertThat(sameAttributes).containsEntry("accountId", 7L).containsKey("authSessionId");

        Map<String, Object> crossAttributes = new HashMap<>();
        assertThat(interceptor.beforeHandshake(
            request(FRONTEND), mock(ServerHttpResponse.class), mock(WebSocketHandler.class), crossAttributes))
            .isTrue();
        assertThat(crossAttributes).containsEntry("accountId", 7L).containsKey("authSessionId");
    }

    @Test
    void rejectsUntrustedOriginInsteadOfUsingWildcard() throws Exception {
        ServerHttpResponse response = mock(ServerHttpResponse.class);

        assertThat(interceptor.beforeHandshake(
            request("https://evil.example"), response, mock(WebSocketHandler.class), new HashMap<>()))
            .isFalse();

        verify(response).setStatusCode(HttpStatus.FORBIDDEN);
    }

    @Test
    void keepsAuthenticationMandatoryWhenOriginIsAbsent() throws Exception {
        MockHttpServletRequest servlet = servletRequest(null);
        servlet.setUserPrincipal(null);

        assertThat(interceptor.beforeHandshake(
            new org.springframework.http.server.ServletServerHttpRequest(servlet),
            mock(ServerHttpResponse.class),
            mock(WebSocketHandler.class),
            new HashMap<>()))
            .isFalse();
    }

    private org.springframework.http.server.ServletServerHttpRequest request(String origin) {
        return new org.springframework.http.server.ServletServerHttpRequest(servletRequest(origin));
    }

    private MockHttpServletRequest servletRequest(String origin) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/ws");
        request.setScheme("https");
        request.setServerName("app.example");
        request.setServerPort(443);
        request.setSession(new MockHttpSession());
        request.setUserPrincipal(() -> "7");
        if (origin != null) {
            request.addHeader("Origin", origin);
        }
        return request;
    }
}
