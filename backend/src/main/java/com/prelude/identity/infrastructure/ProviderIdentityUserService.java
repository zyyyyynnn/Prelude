package com.prelude.identity.infrastructure;

import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Component;

/**
 * Loads the provider identity and keeps the exchange's access token available
 * to the verified-email resolution step.
 */
@Component
public class ProviderIdentityUserService extends DefaultOAuth2UserService {

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        return new ProviderIdentityUser(super.loadUser(userRequest), userRequest.getAccessToken());
    }
}
