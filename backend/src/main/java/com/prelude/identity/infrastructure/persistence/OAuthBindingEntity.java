package com.prelude.identity.infrastructure.persistence;

import com.baomidou.mybatisplus.annotation.TableName;
import com.prelude.identity.domain.OAuthBinding;
import lombok.Data;

/** Row shape of {@code oauth_binding}; keeps the table mapping out of the domain model. */
@Data
@TableName("oauth_binding")
public class OAuthBindingEntity {

    private Long id;
    private Long accountId;
    private String provider;
    private String providerSubject;

    static OAuthBindingEntity of(OAuthBinding binding) {
        OAuthBindingEntity entity = new OAuthBindingEntity();
        entity.setId(binding.getId());
        entity.setAccountId(binding.getAccountId());
        entity.setProvider(binding.getProvider());
        entity.setProviderSubject(binding.getProviderSubject());
        return entity;
    }

    OAuthBinding toDomain() {
        OAuthBinding binding = new OAuthBinding();
        binding.setId(id);
        binding.setAccountId(accountId);
        binding.setProvider(provider);
        binding.setProviderSubject(providerSubject);
        return binding;
    }
}
