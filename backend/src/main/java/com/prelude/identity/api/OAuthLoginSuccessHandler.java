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
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Component;

import java.io.IOException;

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
        String verifiedEmail = null;
        if (authentication.getPrincipal() instanceof ProviderIdentityUser providerIdentity) {
            verifiedEmail = oauthVerifiedEmailResolver.resolveVerifiedEmail(
                provider, providerIdentity, providerIdentity.accessToken());
        }

        AccountPrincipal principal = oauthLoginService.resolveLogin(
            provider, oauthToken.getName(), verifiedEmail, request.getSession());
        if (principal == null) {
            response.sendRedirect("/login?oauth=pending");
            return;
        }
        com.prelude.identity.infrastructure.SessionAuthentication.establish(
            principal, securityContextRepository, sessionAuthenticationStrategy, request, response);
        response.sendRedirect("/interview");
    }
}
