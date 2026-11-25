package com.prelude.identity.infrastructure.security;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SessionCookieSecurityValidatorTest {

    @Test
    void productionWithoutSecureCookieFailsStartup() {
        new ApplicationContextRunner()
            .withUserConfiguration(SessionCookieSecurityValidator.class)
            .withPropertyValues("spring.profiles.active=prod")
            .run(context -> assertThat(context.getStartupFailure())
                .hasRootCauseMessage("SESSION_COOKIE_SECURE=true is required when the prod profile is active"));
    }

    @Test
    void productionWithInsecureCookieFailsStartup() {
        new ApplicationContextRunner()
            .withUserConfiguration(SessionCookieSecurityValidator.class)
            .withPropertyValues(
                "spring.profiles.active=prod",
                "server.servlet.session.cookie.secure=false")
            .run(context -> assertThat(context.getStartupFailure())
                .hasRootCauseMessage("SESSION_COOKIE_SECURE=true is required when the prod profile is active"));
    }

    @Test
    void productionWithSecureCookieStarts() {
        new ApplicationContextRunner()
            .withUserConfiguration(SessionCookieSecurityValidator.class)
            .withPropertyValues(
                "spring.profiles.active=prod",
                "server.servlet.session.cookie.secure=true")
            .run(context -> assertThat(context.getStartupFailure()).isNull());
    }

    @Test
    void developmentKeepsInsecureCookieBehavior() {
        new ApplicationContextRunner()
            .withUserConfiguration(SessionCookieSecurityValidator.class)
            .withPropertyValues(
                "spring.profiles.active=dev",
                "server.servlet.session.cookie.secure=false")
            .run(context -> assertThat(context.getStartupFailure()).isNull());
    }
}
