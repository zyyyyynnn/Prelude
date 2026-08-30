package com.prelude.identity.infrastructure;

import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.oauth2.core.OAuth2AccessToken;

import java.io.Serializable;
import java.util.Collection;
import java.util.Map;

/**
 * OAuth2User wrapper that carries the access token of the login exchange so the
 * verified-email resolver can query provider endpoints authoritatively.
 */
public record ProviderIdentityUser(OAuth2User delegate, OAuth2AccessToken accessToken) implements OAuth2User, Serializable {

    @Override
    public Map<String, Object> getAttributes() {
        return delegate.getAttributes();
    }

    @Override
    public Collection<? extends org.springframework.security.core.GrantedAuthority> getAuthorities() {
        return delegate.getAuthorities();
    }

    @Override
    public String getName() {
        return delegate.getName();
    }
}
