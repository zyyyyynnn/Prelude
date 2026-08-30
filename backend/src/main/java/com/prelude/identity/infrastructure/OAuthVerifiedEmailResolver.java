package com.prelude.identity.infrastructure;

import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Component;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

/**
 * Resolves the provider-verified email of an OAuth identity. Google identities
 * carry the verified claim in the profile attributes; GitHub exposes explicit
 * verification status only through its emails endpoint, which is called with
 * the access token of the current exchange. Identities without a verified
 * email resolve to null and never trigger account discovery.
 */
@Component
public class OAuthVerifiedEmailResolver {

    private static final String GITHUB_EMAILS_URL = "https://api.github.com/user/emails";

    private final RestClient restClient;

    @org.springframework.beans.factory.annotation.Autowired
    public OAuthVerifiedEmailResolver() {
        this.restClient = RestClient.builder().build();
    }

    OAuthVerifiedEmailResolver(RestClient.Builder restClientBuilder) {
        this.restClient = restClientBuilder.build();
    }

    public String resolveVerifiedEmail(String registrationId, OAuth2User user, OAuth2AccessToken accessToken) {
        if ("github".equals(registrationId)) {
            return githubVerifiedEmail(accessToken);
        }
        return verifiedClaimEmail(user.getAttributes());
    }

    private String verifiedClaimEmail(Map<String, Object> attributes) {
        if (!(attributes.get("email_verified") instanceof Boolean verified) || !verified) {
            return null;
        }
        if (attributes.get("email") instanceof String email && !email.isBlank()) {
            return email;
        }
        return null;
    }

    private String githubVerifiedEmail(OAuth2AccessToken accessToken) {
        if (accessToken == null || !accessToken.getTokenType().getValue().equalsIgnoreCase("bearer")) {
            return null;
        }
        List<Map<String, Object>> emails = restClient.get()
            .uri(GITHUB_EMAILS_URL)
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken.getTokenValue())
            .header(HttpHeaders.ACCEPT, "application/vnd.github+json")
            .retrieve()
            .body(new ParameterizedTypeReference<List<Map<String, Object>>>() {
            });
        if (emails == null) {
            return null;
        }
        String firstVerified = null;
        for (Map<String, Object> entry : emails) {
            if (!Boolean.TRUE.equals(entry.get("verified"))) {
                continue;
            }
            if (!(entry.get("email") instanceof String email) || email.isBlank()) {
                continue;
            }
            if (Boolean.TRUE.equals(entry.get("primary"))) {
                return email;
            }
            if (firstVerified == null) {
                firstVerified = email;
            }
        }
        return firstVerified;
    }
}
