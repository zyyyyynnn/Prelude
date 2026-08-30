package com.prelude.identity.infrastructure;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.oidc.IdTokenClaimNames;

import java.util.ArrayList;
import java.util.List;

/**
 * OAuth2 client registrations are built from deployment configuration only.
 * Without configured credentials no repository exists and password login keeps working.
 */
@Configuration
@EnableConfigurationProperties(OAuthClientConfiguration.OAuthClientProperties.class)
public class OAuthClientConfiguration {

    @Bean
    @Conditional(OAuthClientsConfiguredCondition.class)
    public ClientRegistrationRepository clientRegistrationRepository(OAuthClientProperties properties) {
        List<ClientRegistration> registrations = new ArrayList<>();
        if (isConfigured(properties.google().clientId())) {
            // Explicit official Google OIDC metadata: the JWK Set URI makes Spring
            // Security process the login as OIDC and validate the Google ID token.
            registrations.add(ClientRegistration.withRegistrationId("google")
                .clientId(properties.google().clientId())
                .clientSecret(properties.google().clientSecret())
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                .scope("openid", "profile", "email")
                .authorizationUri("https://accounts.google.com/o/oauth2/v2/auth")
                .tokenUri("https://www.googleapis.com/oauth2/v4/token")
                .jwkSetUri("https://www.googleapis.com/oauth2/v3/certs")
                .userInfoUri("https://www.googleapis.com/oauth2/v3/userinfo")
                .userNameAttributeName(IdTokenClaimNames.SUB)
                .clientName("Google")
                .build());
        }
        if (isConfigured(properties.github().clientId())) {
            registrations.add(ClientRegistration.withRegistrationId("github")
                .clientId(properties.github().clientId())
                .clientSecret(properties.github().clientSecret())
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                .scope("read:user", "user:email")
                .authorizationUri("https://github.com/login/oauth/authorize")
                .tokenUri("https://github.com/login/oauth/access_token")
                .userInfoUri("https://api.github.com/user")
                .userNameAttributeName("id")
                .clientName("GitHub")
                .build());
        }
        return new InMemoryClientRegistrationRepository(registrations);
    }

    private boolean isConfigured(String value) {
        return value != null && !value.isBlank();
    }

    static class OAuthClientsConfiguredCondition implements Condition {

        @Override
        public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            return isConfigured(context, "prelude.oauth.google.client-id")
                || isConfigured(context, "prelude.oauth.github.client-id");
        }

        private boolean isConfigured(ConditionContext context, String key) {
            String value = context.getEnvironment().getProperty(key);
            return value != null && !value.isBlank();
        }
    }

    @ConfigurationProperties(prefix = "prelude.oauth")
    public record OAuthClientProperties(Provider google, Provider github) {

        public record Provider(String clientId, String clientSecret) {
        }
    }
}
