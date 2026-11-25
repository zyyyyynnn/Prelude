package com.prelude.identity.web;

import com.prelude.identity.api.security.OriginPolicy;
import com.prelude.identity.infrastructure.security.CorsProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OriginValidationFilterTest {

    private static final String FRONTEND = "https://frontend.example";
    private AnnotationConfigApplicationContext context;
    private OriginValidationFilter filter;

    @BeforeEach
    void createFilter() {
        context = new AnnotationConfigApplicationContext();
        context.registerBean(CorsProperties.class, () -> new CorsProperties(List.of(FRONTEND)));
        context.scan("com.prelude.identity.infrastructure.security");
        context.refresh();
        filter = new OriginValidationFilter(context.getBean(OriginPolicy.class));
    }

    @AfterEach
    void closeContext() {
        context.close();
    }

    @Test
    void allowsSameOriginAndConfiguredCrossOriginUnsafeRequests() throws Exception {
        MockHttpServletRequest sameOrigin = request("POST", "https://app.example");
        MockHttpServletResponse sameResponse = new MockHttpServletResponse();
        MockFilterChain sameChain = new MockFilterChain();

        filter.doFilter(sameOrigin, sameResponse, sameChain);

        assertThat(sameResponse.getStatus()).isEqualTo(200);
        assertThat(sameChain.getRequest()).isSameAs(sameOrigin);

        MockHttpServletRequest crossOrigin = request("POST", FRONTEND);
        MockHttpServletResponse crossResponse = new MockHttpServletResponse();
        MockFilterChain crossChain = new MockFilterChain();

        filter.doFilter(crossOrigin, crossResponse, crossChain);

        assertThat(crossResponse.getStatus()).isEqualTo(200);
        assertThat(crossChain.getRequest()).isSameAs(crossOrigin);
    }

    @Test
    void rejectsUntrustedUnsafeOriginWithProblemBody() throws Exception {
        MockHttpServletRequest request = request("DELETE", "https://evil.example");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).contains("\"code\":\"origin_rejected\"");
        assertThat(chain.getRequest()).isNull();
    }

    @Test
    void keepsSafeRequestsUnaffectedByOriginValidation() throws Exception {
        MockHttpServletRequest request = request("GET", "https://evil.example");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(chain.getRequest()).isSameAs(request);
    }

    private MockHttpServletRequest request(String method, String origin) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, "/api/resource");
        request.setScheme("https");
        request.setServerName("app.example");
        request.setServerPort(443);
        request.addHeader("Origin", origin);
        return request;
    }
}
