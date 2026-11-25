package com.prelude.identity.infrastructure.security;

import com.prelude.identity.api.security.OriginPolicy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OriginPolicyTest {

    private static final String FRONTEND = "https://frontend.example";
    private AnnotationConfigApplicationContext context;

    @BeforeEach
    void createPolicy() {
        context = new AnnotationConfigApplicationContext();
        context.registerBean(CorsProperties.class, () -> new CorsProperties(List.of(FRONTEND)));
        context.register(DefaultOriginPolicy.class);
        context.refresh();
    }

    @AfterEach
    void closeContext() {
        context.close();
    }

    @Test
    void exposesOnlyExplicitConfiguredOriginsToCors() {
        OriginPolicy policy = context.getBean(OriginPolicy.class);

        assertThat(policy.allowedOrigins()).containsExactly(FRONTEND);
        assertThat(policy.isAllowed(FRONTEND)).isTrue();
        assertThat(policy.isAllowed("https://other.example")).isFalse();
        assertThat(policy.isAllowed("*")).isFalse();
    }

    @Test
    void acceptsSameOriginWithDefaultOrExplicitPorts() {
        OriginPolicy policy = context.getBean(OriginPolicy.class);

        assertThat(policy.isAllowed("http://localhost", request("http", "localhost", 80))).isTrue();
        assertThat(policy.isAllowed("https://app.example", request("https", "app.example", 443))).isTrue();
        assertThat(policy.isAllowed("http://localhost:8080", request("http", "localhost", 8080))).isTrue();
        assertThat(policy.isAllowed(FRONTEND, request("https", "app.example", 443))).isTrue();
    }

    @Test
    void rejectsDifferentOriginsAndMalformedOriginValues() {
        OriginPolicy policy = context.getBean(OriginPolicy.class);
        var app = request("https", "app.example", 443);

        assertThat(policy.isAllowed("http://app.example", app)).isFalse();
        assertThat(policy.isAllowed("https://app.example:444", app)).isFalse();
        assertThat(policy.isAllowed("https://app.example.evil.test", app)).isFalse();
        assertThat(policy.isAllowed("https://app.example/path", app)).isFalse();
        assertThat(policy.isAllowed("https://user@app.example", app)).isFalse();
        assertThat(policy.isAllowed("null", app)).isFalse();
        assertThat(policy.isAllowed(null, app)).isFalse();
    }

    @Test
    void refusesWildcardConfigurationAtStartup() {
        context.close();
        context = new AnnotationConfigApplicationContext();
        context.registerBean(CorsProperties.class, () -> new CorsProperties(List.of("*")));

        assertThatThrownBy(() -> {
            context.register(DefaultOriginPolicy.class);
            context.refresh();
        }).hasRootCauseMessage("app.cors.allowed-origins must contain explicit HTTP(S) origins");
    }

    private OriginPolicy.RequestOrigin request(String scheme, String host, int port) {
        return new OriginPolicy.RequestOrigin(scheme, host, port);
    }
}
