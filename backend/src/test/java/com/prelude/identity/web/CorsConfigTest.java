package com.prelude.identity.web;

import com.prelude.identity.api.security.OriginPolicy;
import com.prelude.identity.infrastructure.security.CorsProperties;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.filter.CorsFilter;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class CorsConfigTest {

    private static final String FRONTEND = "https://frontend.example";
    private AnnotationConfigApplicationContext context;
    private CorsFilter corsFilter;

    @BeforeEach
    void createCorsFilter() {
        context = new AnnotationConfigApplicationContext();
        context.registerBean(CorsProperties.class, () -> new CorsProperties(List.of(FRONTEND)));
        context.scan("com.prelude.identity.infrastructure.security");
        context.refresh();
        corsFilter = new CorsConfig().corsFilterRegistration(context.getBean(OriginPolicy.class)).getFilter();
    }

    @AfterEach
    void closeContext() {
        context.close();
    }

    @Test
    void allowsConfiguredCredentialedPreflight() throws Exception {
        MockHttpServletRequest request = preflight(FRONTEND);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        corsFilter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getHeader("Access-Control-Allow-Origin")).isEqualTo(FRONTEND);
        assertThat(response.getHeader("Access-Control-Allow-Credentials")).isEqualTo("true");
        verify(chain, never()).doFilter(request, response);
    }

    @Test
    void rejectsUnconfiguredPreflightWithoutWildcardFallback() throws Exception {
        MockHttpServletRequest request = preflight("https://other.example");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        corsFilter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getHeader("Access-Control-Allow-Origin")).isNull();
        verify(chain, never()).doFilter(request, response);
    }

    private MockHttpServletRequest preflight(String origin) {
        MockHttpServletRequest request = new MockHttpServletRequest("OPTIONS", "/api/auth/login");
        request.addHeader("Origin", origin);
        request.addHeader("Access-Control-Request-Method", "POST");
        return request;
    }
}
