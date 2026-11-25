package com.prelude.identity.infrastructure.security;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

@Component
class SessionCookieSecurityValidator implements InitializingBean {

    private final Environment environment;

    SessionCookieSecurityValidator(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void afterPropertiesSet() {
        if (environment.acceptsProfiles(Profiles.of("prod"))
            && !environment.getProperty("server.servlet.session.cookie.secure", Boolean.class, false)) {
            throw new IllegalStateException("SESSION_COOKIE_SECURE=true is required when the prod profile is active");
        }
    }
}
