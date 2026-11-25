package com.prelude.identity.web;

import com.prelude.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class OAuthProviderControllerTest {

    @Test
    void noConfiguredClientsReturnsAnEmptyProviderList() throws Exception {
        mvc(null).perform(get("/api/auth/oauth/providers"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    void providerListContainsOnlyRegisteredClients() throws Exception {
        var repository = new InMemoryClientRegistrationRepository(
            registration("google"), registration("github"));

        mvc(repository).perform(get("/api/auth/oauth/providers"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.length()").value(2))
            .andExpect(jsonPath("$.data[0]").value("google"))
            .andExpect(jsonPath("$.data[1]").value("github"));
    }

    @Test
    void startRemembersASafeRedirectAndDelegatesToSpringAuthorization() throws Exception {
        var repository = new InMemoryClientRegistrationRepository(registration("google"));
        MockHttpSession session = new MockHttpSession();

        mvc(repository).perform(get("/api/auth/oauth/google/start")
                .param("redirect", "/analytics")
                .session(session))
            .andExpect(status().isFound())
            .andExpect(result -> assertThat(result.getResponse().getRedirectedUrl())
                .isEqualTo("/oauth2/authorization/google"));

        assertThat(session.getAttribute(OAuthLoginRedirect.SESSION_ATTRIBUTE)).isEqualTo("/analytics");
    }

    @Test
    void startReplacesAnUnsafeRedirectWithTheDefault() throws Exception {
        var repository = new InMemoryClientRegistrationRepository(registration("github"));
        MockHttpSession session = new MockHttpSession();

        mvc(repository).perform(get("/api/auth/oauth/github/start")
                .param("redirect", "https://evil.example")
                .session(session))
            .andExpect(status().isFound());

        assertThat(session.getAttribute(OAuthLoginRedirect.SESSION_ATTRIBUTE)).isEqualTo("/interview");
    }

    @Test
    void startRejectsAProviderThatIsNotEnabled() throws Exception {
        mvc(new InMemoryClientRegistrationRepository(registration("google")))
            .perform(get("/api/auth/oauth/github/start"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("not_found"));
    }

    private MockMvc mvc(ClientRegistrationRepository repository) {
        DefaultListableBeanFactory beans = new DefaultListableBeanFactory();
        if (repository != null) {
            beans.registerSingleton("clientRegistrationRepository", repository);
        }
        var controller = new OAuthProviderController(beans.getBeanProvider(ClientRegistrationRepository.class));
        return MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
    }

    private ClientRegistration registration(String id) {
        return ClientRegistration.withRegistrationId(id)
            .clientId("client-id")
            .clientSecret("client-secret")
            .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
            .scope("openid")
            .authorizationUri("https://example.com/authorize")
            .tokenUri("https://example.com/token")
            .userInfoUri("https://example.com/userinfo")
            .userNameAttributeName("sub")
            .clientName(id)
            .build();
    }
}
