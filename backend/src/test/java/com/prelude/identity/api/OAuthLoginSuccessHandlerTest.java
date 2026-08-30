package com.prelude.identity.api;

import com.prelude.identity.AccountPrincipal;
import com.prelude.identity.application.OAuthLoginService;
import com.prelude.identity.infrastructure.OAuthVerifiedEmailResolver;
import com.prelude.identity.infrastructure.ProviderIdentityUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.oidc.IdTokenClaimNames;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.SecurityContextRepository;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OAuthLoginSuccessHandlerTest {

    private final OAuthLoginService oauthLoginService = mock(OAuthLoginService.class);
    private final OAuthVerifiedEmailResolver emailResolver = mock(OAuthVerifiedEmailResolver.class);
    private final SecurityContextRepository securityContextRepository = mock(SecurityContextRepository.class);
    private final SessionAuthenticationStrategy sessionAuthenticationStrategy = mock(SessionAuthenticationStrategy.class);
    private final OAuthLoginSuccessHandler handler = new OAuthLoginSuccessHandler(
        oauthLoginService, emailResolver, securityContextRepository, sessionAuthenticationStrategy);

    private final MockHttpServletRequest request = new MockHttpServletRequest();
    private final MockHttpServletResponse response = new MockHttpServletResponse();
    private final MockHttpSession session = new MockHttpSession();

    @BeforeEach
    void keepSessionOnRequest() {
        request.setSession(session);
        when(oauthLoginService.resolveLogin(any(), any(), any(), any()))
            .thenReturn(new AccountPrincipal(7L, "owner"));
    }

    @Test
    void googleOidcUsesTheSubClaimAndTheVerifiedIdTokenEmail() throws Exception {
        OidcUser oidcUser = oidcUser(Map.of(
            IdTokenClaimNames.SUB, "google-sub-1",
            "email", "owner@example.com",
            "email_verified", true));
        var authentication = token("google", oidcUser);

        handler.onAuthenticationSuccess(request, response, authentication);

        verify(oauthLoginService).resolveLogin("google", "google-sub-1", "owner@example.com", session);
        assertThat(response.getRedirectedUrl()).isEqualTo("/interview");
    }

    @Test
    void googleOidcUnverifiedEmailIsNeverUsedForDiscovery() throws Exception {
        OidcUser oidcUser = oidcUser(Map.of(
            IdTokenClaimNames.SUB, "google-sub-2",
            "email", "owner@example.com",
            "email_verified", false));
        var authentication = token("google", oidcUser);

        handler.onAuthenticationSuccess(request, response, authentication);

        verify(oauthLoginService).resolveLogin(eq("google"), eq("google-sub-2"), isNull(), eq(session));
    }

    @Test
    void githubResolvesTheVerifiedEmailThroughTheAuthoritativeEmailsEndpoint() throws Exception {
        OAuth2User githubUser = new DefaultOAuth2User(
            List.of(new SimpleGrantedAuthority("ROLE_USER")),
            Map.of("id", 42, "login", "owner-login"), "id");
        var accessToken = new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER, "token-1", null, null);
        var authentication = token("github", new ProviderIdentityUser(githubUser, accessToken));
        when(emailResolver.resolveVerifiedEmail(any(ProviderIdentityUser.class), eq(accessToken)))
            .thenReturn("owner@example.com");

        handler.onAuthenticationSuccess(request, response, authentication);

        verify(oauthLoginService).resolveLogin("github", "42", "owner@example.com", session);
    }

    @Test
    void aPendingCollisionRedirectsToTheLoginHintWithoutEstablishingASession() throws Exception {
        when(oauthLoginService.resolveLogin(any(), any(), any(), any())).thenReturn(null);
        OidcUser oidcUser = oidcUser(Map.of(
            IdTokenClaimNames.SUB, "google-sub-1",
            "email", "owner@example.com",
            "email_verified", true));

        handler.onAuthenticationSuccess(request, response, token("google", oidcUser));

        assertThat(response.getRedirectedUrl()).isEqualTo("/login?oauth=pending");
        org.mockito.Mockito.verifyNoInteractions(sessionAuthenticationStrategy);
    }

    private OidcUser oidcUser(Map<String, Object> claims) {
        OidcIdToken idToken = new OidcIdToken("id-token-value",
            Instant.now(), Instant.now().plusSeconds(60), claims);
        OidcUserInfo userInfo = new OidcUserInfo(claims);
        return new DefaultOidcUser(List.of(new SimpleGrantedAuthority("ROLE_USER")),
            idToken, userInfo, IdTokenClaimNames.SUB);
    }

    private OAuth2AuthenticationToken token(String registrationId, OAuth2User principal) {
        return new OAuth2AuthenticationToken(
            principal, principal.getAuthorities(), registrationId);
    }
}
