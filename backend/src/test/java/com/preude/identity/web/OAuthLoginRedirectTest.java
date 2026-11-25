package com.prelude.identity.web;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class OAuthLoginRedirectTest {

    @ParameterizedTest
    @ValueSource(strings = {
        "https://evil.example/analytics",
        "//evil.example/analytics",
        "/\\evil.example",
        "/settings",
        "/interview/../analytics",
        "/interview%2f%2fevil.example"
    })
    void unsafeRedirectsUseTheLocalDefault(String redirect) {
        assertThat(OAuthLoginRedirect.sanitize(redirect)).isEqualTo("/interview");
    }

    @ParameterizedTest
    @ValueSource(strings = {"/", "/interview", "/analytics", "/analytics?range=30d#trend"})
    void knownApplicationRoutesArePreserved(String redirect) {
        assertThat(OAuthLoginRedirect.sanitize(redirect)).isEqualTo(redirect);
    }
}
