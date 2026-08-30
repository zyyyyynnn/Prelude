package com.prelude.identity.infrastructure;

import com.prelude.identity.AccountPrincipal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Session validity contract against real Spring Session Redis: the originating
 * session must exist and carry the same account principal.
 */
@EnabledIfEnvironmentVariable(named = "PRELUDE_IDENTITY_SMOKE", matches = "true")
@SpringBootTest
class SpringSessionValidityTest {

    @Autowired
    private SpringSessionValidity sessionValidity;

    @Autowired
    private org.springframework.session.data.redis.RedisIndexedSessionRepository sessionRepository;

    @Test
    void validityRequiresTheSameAccountAndADeletedSessionFailsClosed() {
        MockHttpSession carrier = new MockHttpSession();
        carrier.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,
            new SecurityContextImpl(UsernamePasswordAuthenticationToken.authenticated(
                new AccountPrincipal(7L, "owner"), null, List.of())));
        var session = sessionRepository.createSession();
        for (String name : java.util.Collections.list(carrier.getAttributeNames())) {
            session.setAttribute(name, carrier.getAttribute(name));
        }
        sessionRepository.save(session);

        assertThat(sessionValidity.isActive(session.getId(), 7L)).isTrue();
        assertThat(sessionValidity.isActive(session.getId(), 8L)).isFalse();

        sessionRepository.deleteById(session.getId());
        assertThat(sessionValidity.isActive(session.getId(), 7L)).isFalse();
        assertThat(sessionValidity.isActive(null, 7L)).isFalse();
    }
}
