package com.prelude.identity.infrastructure.persistence;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.prelude.identity.api.port.OAuthBindingRepository;
import com.prelude.identity.domain.OAuthBinding;

public interface OAuthBindingMapper extends BaseMapper<OAuthBindingEntity>, OAuthBindingRepository {

    @Override
    default OAuthBinding findByProviderAndSubject(String provider, String providerSubject) {
        return single(new LambdaQueryWrapper<OAuthBindingEntity>()
            .eq(OAuthBindingEntity::getProvider, provider)
            .eq(OAuthBindingEntity::getProviderSubject, providerSubject));
    }

    @Override
    default OAuthBinding findByAccountAndProvider(long accountId, String provider) {
        return single(new LambdaQueryWrapper<OAuthBindingEntity>()
            .eq(OAuthBindingEntity::getAccountId, accountId)
            .eq(OAuthBindingEntity::getProvider, provider));
    }

    @Override
    default void add(OAuthBinding binding) {
        OAuthBindingEntity entity = OAuthBindingEntity.of(binding);
        insert(entity);
        binding.setId(entity.getId());
    }

    private OAuthBinding single(LambdaQueryWrapper<OAuthBindingEntity> query) {
        OAuthBindingEntity entity = selectOne(query.last("LIMIT 1"));
        return entity == null ? null : entity.toDomain();
    }
}
