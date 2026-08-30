package com.prelude.identity.api;

import com.prelude.identity.AccountPrincipal;
import com.prelude.identity.application.OAuthLoginService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
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
        OAuth2User user = oauthToken.getPrincipal();
        String provider = oauthToken.getAuthorizedClientRegistrationId();
        String verifiedEmail = extractVerifiedEmail(provider, user.getAttributes());

        AccountPrincipal principal = oauthLoginService.resolveLogin(
            provider, user.getName(), verifiedEmail, request.getSession());
        if (principal == null) {
            response.sendRedirect("/login?oauth=pending");
            return;
        }
        com.prelude.identity.infrastructure.SessionAuthentication.establish(
            principal, securityContextRepository, sessionAuthenticationStrategy, request, response);
        response.sendRedirect("/interview");
    }

    private String extractVerifiedEmail(String provider, Map<String, Object> attributes) {
        if (!(attributes.get("email") instanceof String email) || email.isBlank()) {
            return null;
        }
        Object verified = attributes.get("email_verified");
        if (verified instanceof Boolean flag) {
            return flag ? email : null;
        }
        // Providers without an explicit verified flag expose only their verified addresses here.
        return "github".equals(provider) ? email : null;
    }
}
