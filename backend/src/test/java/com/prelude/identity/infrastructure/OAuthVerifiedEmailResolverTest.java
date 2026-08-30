package com.prelude.identity.infrastructure;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2UserAuthority;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class OAuthVerifiedEmailResolverTest {

    private final RestClient.Builder builder = RestClient.builder();
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    private final OAuthVerifiedEmailResolver resolver = new OAuthVerifiedEmailResolver(builder);

    private final OAuth2AccessToken token =
        new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER, "token-1", null, null);

    @Test
    void githubVerifiedPrimaryEmailIsUsedForDiscovery() {
        server.expect(requestTo("https://api.github.com/user/emails"))
            .andExpect(header("Authorization", "Bearer token-1"))
            .andRespond(withSuccess("""
                [
                  {"email":"pub@example.com","primary":false,"verified":false},
                  {"email":"secondary@example.com","primary":false,"verified":true},
                  {"email":"real@example.com","primary":true,"verified":true}
                ]
                """, MediaType.APPLICATION_JSON));

        String email = resolver.resolveVerifiedEmail(user("pub@example.com"), token);

        assertThat(email).isEqualTo("real@example.com");
        server.verify();
    }

    @Test
    void githubPublicEmailWithoutVerifiedEvidenceIsNeverUsedForDiscovery() {
        server.expect(requestTo("https://api.github.com/user/emails"))
            .andRespond(withSuccess("""
                [{"email":"pub@example.com","primary":true,"verified":false}]
                """, MediaType.APPLICATION_JSON));

        String email = resolver.resolveVerifiedEmail(user("pub@example.com"), token);

        assertThat(email).isNull();
        server.verify();
    }

    @Test
    void aMissingTokenYieldsNoVerifiedEmail() {
        assertThat(resolver.resolveVerifiedEmail(user("pub@example.com"), null)).isNull();
        server.verify();
    }

    private OAuth2User user(String email) {
        return user(Map.of("email", email));
    }

    private OAuth2User user(Map<String, Object> attributes) {
        return new DefaultOAuth2User(List.of(new OAuth2UserAuthority(attributes)), attributes, "email");
    }
}
