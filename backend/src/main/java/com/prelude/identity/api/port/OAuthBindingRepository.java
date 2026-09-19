package com.prelude.identity.api.port;

import com.prelude.identity.domain.OAuthBinding;

public interface OAuthBindingRepository {

    OAuthBinding findByProviderAndSubject(String provider, String providerSubject);

    OAuthBinding findByAccountAndProvider(long accountId, String provider);

    void add(OAuthBinding binding);
}
