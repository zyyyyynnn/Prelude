package com.prelude.identity.web;

import com.prelude.GlobalExceptionHandler;
import com.prelude.identity.AccountPrincipal;
import com.prelude.identity.api.CurrentAccount;
import com.prelude.identity.application.AuthenticationService;
import com.prelude.identity.application.SessionRevokeService;
import com.prelude.identity.application.port.AccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.http.MediaType;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.filter.ForwardedHeaderFilter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.handler.MappedInterceptor;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthenticationRateLimitWebTest {

    private AuthenticationService authenticationService;
    private StubRedis redis;
    private AuthController controller;
    private AuthenticationRateLimitProperties properties;

    @BeforeEach
    void createController() {
        authenticationService = mock(AuthenticationService.class);
        when(authenticationService.login(any(), any(), any())).thenReturn(new AccountPrincipal(7L, "candidate"));
        redis = new StubRedis();
        redis.results.add(1L);
        properties = new AuthenticationRateLimitProperties(true, 10, 5);
        controller = new AuthController(
            authenticationService,
            mock(SessionRevokeService.class),
            mock(AccountRepository.class),
            mock(CurrentAccount.class),
            mock(SecurityContextRepository.class),
            mock(SessionAuthenticationStrategy.class)
        );
    }

    @Test
    void loginLimitIsGenericAndIndependentOfTheRequestedUsername() throws Exception {
        redis.results.clear();
        redis.results.add(0L);
        redis.results.add(0L);
        MockMvc mvc = mockMvc(interceptor(properties));

        mvc.perform(login("existing"))
            .andExpect(status().isTooManyRequests())
            .andExpect(jsonPath("$.code").value("rate_limited"))
            .andExpect(jsonPath("$.detail").value("认证请求过于频繁，请稍后再试"));
        mvc.perform(login("missing"))
            .andExpect(status().isTooManyRequests())
            .andExpect(jsonPath("$.code").value("rate_limited"))
            .andExpect(jsonPath("$.detail").value("认证请求过于频繁，请稍后再试"));

        verifyNoInteractions(authenticationService);
        assertThat(redis.keys).hasSize(2);
        assertThat(redis.keys.getFirst()).isEqualTo(redis.keys.getLast());
        assertThat(String.join("", redis.keys)).doesNotContain("existing", "missing");
    }

    @Test
    void encodedPathVariantsShareTheMatchedLoginBucket() throws Exception {
        redis.results.clear();
        redis.results.add(0L);
        redis.results.add(0L);
        MockMvc mvc = mockMvc(interceptor(properties));

        mvc.perform(post(java.net.URI.create("/api/auth/%6cogin"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"username":"candidate","password":"correct-horse"}
                    """))
            .andExpect(status().isTooManyRequests())
            .andExpect(jsonPath("$.code").value("rate_limited"));
        mvc.perform(login("candidate"))
            .andExpect(status().isTooManyRequests())
            .andExpect(jsonPath("$.code").value("rate_limited"));

        verifyNoInteractions(authenticationService);
        assertThat(redis.keys).hasSize(2);
        assertThat(redis.keys.getFirst()).isEqualTo(redis.keys.getLast());
    }

    @Test
    void forwardedClientAddressesUseSeparateBucketsBehindTheTrustedProxy() throws Exception {
        MockMvc mvc = mockMvc(interceptor(properties));
        mvc.perform(login("candidate").header("X-Forwarded-For", "198.51.100.1"))
            .andExpect(status().isOk());
        redis.results.add(1L);
        mvc.perform(login("candidate").header("X-Forwarded-For", "198.51.100.2"))
            .andExpect(status().isOk());

        assertThat(redis.keys).hasSize(2);
        assertThat(redis.keys.getFirst()).isNotEqualTo(redis.keys.getLast());
    }

    @Test
    void registrationUsesTheSameRateLimitContract() throws Exception {
        redis.results.clear();
        redis.results.add(0L);

        mockMvc(interceptor(properties)).perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"username":"candidate","password":"correct-horse"}
                    """))
            .andExpect(status().isTooManyRequests())
            .andExpect(jsonPath("$.code").value("rate_limited"))
            .andExpect(jsonPath("$.detail").value("认证请求过于频繁，请稍后再试"));

        verifyNoInteractions(authenticationService);
    }

    @Test
    void nonAuthenticationEndpointsAreNotRateLimited() throws Exception {
        redis.results.clear();
        redis.results.add(0L);

        mockMvc(interceptor(properties)).perform(post("/api/auth/logout"))
            .andExpect(status().isOk());

        assertThat(redis.keys).isEmpty();
    }

    @Test
    void disablingTheLimitBypassesRedis() throws Exception {
        redis.results.clear();
        redis.failure = new IllegalStateException("redis must not be called");

        mockMvc(interceptor(new AuthenticationRateLimitProperties(false, 10, 5)))
            .perform(login("candidate"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.accountId").value(7));

        assertThat(redis.keys).isEmpty();
    }

    @Test
    void redisFailureDoesNotSilentlyDisableTheLimit() throws Exception {
        redis.failure = new IllegalStateException("redis unavailable");

        mockMvc(interceptor(properties)).perform(login("candidate"))
            .andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.code").value("rate_limit_unavailable"));
    }

    @Test
    void loginAndRegistrationUseSeparateBuckets() throws Exception {
        MockMvc mvc = mockMvc(interceptor(properties));
        mvc.perform(login("candidate")).andExpect(status().isOk());
        redis.results.add(1L);
        mvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"username":"candidate","password":"correct-horse"}
                    """))
            .andExpect(status().isOk());

        assertThat(redis.keys).hasSize(2);
        assertThat(redis.keys.getFirst()).isNotEqualTo(redis.keys.getLast());
    }

    private AuthenticationRateLimitInterceptor interceptor(AuthenticationRateLimitProperties properties) {
        return new AuthenticationRateLimitInterceptor(redis, properties);
    }

    private MockMvc mockMvc(AuthenticationRateLimitInterceptor interceptor) {
        ExposedInterceptorRegistry registry = new ExposedInterceptorRegistry();
        new IdentityWebMvcConfig(interceptor).addInterceptors(registry);
        var builder = MockMvcBuilders.standaloneSetup(controller)
            .addFilters(new ForwardedHeaderFilter())
            .setControllerAdvice(new GlobalExceptionHandler());
        registry.exposedInterceptors().stream()
            .map(MappedInterceptor.class::cast)
            .forEach(mapped -> builder.addMappedInterceptors(mapped.getIncludePathPatterns(), mapped.getInterceptor()));
        return builder.build();
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder login(String username) {
        return post("/api/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"username":"%s","password":"correct-horse"}
                """.formatted(username));
    }

    private static final class ExposedInterceptorRegistry extends InterceptorRegistry {
        List<Object> exposedInterceptors() {
            return getInterceptors();
        }
    }

    private static final class StubRedis extends StringRedisTemplate {
        private final Deque<Long> results = new ArrayDeque<>();
        private final List<String> keys = new ArrayList<>();
        private RuntimeException failure;

        @Override
        @SuppressWarnings("unchecked")
        public <T> T execute(RedisScript<T> script, List<String> scriptKeys, Object... args) {
            keys.add(scriptKeys.getFirst());
            if (failure != null) {
                throw failure;
            }
            if (results.isEmpty()) {
                throw new AssertionError("Unexpected Redis call");
            }
            return (T) results.removeFirst();
        }
    }
}
