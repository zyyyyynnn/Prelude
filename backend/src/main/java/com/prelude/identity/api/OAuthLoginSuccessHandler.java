package com.prelude.identity.api;

import com.prelude.identity.AccountPrincipal;
import com.prelude.identity.application.OAuthLoginService;
import com.prelude.identity.infrastructure.OAuthVerifiedEmailResolver;
import com.prelude.identity.infrastructure.ProviderIdentityUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class OAuthLoginSuccessHandler implements AuthenticationSuccessHandler {

    private final OAuthLoginService oauthLoginService;
    private final OAuthVerifiedEmailResolver oauthVerifiedEmailResolver;
    private final SecurityContextRepository securityContextRepository;
    private final SessionAuthenticationStrategy sessionAuthenticationStrategy;

    @Override
    public void onAuthenticationSuccess(
        HttpServletRequest request,
        HttpServletResponse response,
        Authentication authentication
    ) throws IOException {
        if (!(authentication instanceof OAuth2AuthenticationToken oauthToken)) {
            response.sendRedirect("/login");
            return;
        }
        String provider = oauthToken.getAuthorizedClientRegistrationId();

        String subject;
        String verifiedEmail = null;
        if (authentication.getPrincipal() instanceof OidcUser oidcUser) {
            // Google (OIDC): the verified claim comes from the validated ID token.
            subject = oidcUser.getSubject();
            Map<String, Object> claims = oidcUser.getClaims();
            if (Boolean.TRUE.equals(claims.get("email_verified"))
                && claims.get("email") instanceof String email
                && !email.isBlank()) {
                verifiedEmail = email;
            }
        } else if (authentication.getPrincipal() instanceof ProviderIdentityUser providerIdentity) {
            // GitHub (OAuth2): the verified/primary email comes from the provider's
            // emails endpoint, called with the exchange's access token.
            subject = oauthToken.getName();
            verifiedEmail = oauthVerifiedEmailResolver.resolveVerifiedEmail(
                providerIdentity, providerIdentity.accessToken());
        } else {
            response.sendRedirect("/login");
            return;
        }

        AccountPrincipal principal = oauthLoginService.resolveLogin(
            provider, subject, verifiedEmail, request.getSession());
        if (principal == null) {
            response.sendRedirect("/login?oauth=pending");
            return;
        }
        com.prelude.identity.infrastructure.SessionAuthentication.establish(
            principal, securityContextRepository, sessionAuthenticationStrategy, request, response);
        response.sendRedirect("/interview");
    }
}
