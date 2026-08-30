package com.prelude.identity.infrastructure;

import com.prelude.BusinessException;
import com.prelude.identity.AccountPrincipal;
import com.prelude.identity.api.CurrentAccount;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class SpringSecurityCurrentAccount implements CurrentAccount {

    @Override
    public Long idOrNull() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }
        if (authentication.getPrincipal() instanceof AccountPrincipal principal) {
            return principal.accountId();
        }
        return null;
    }

    @Override
    public long requireId() {
        Long accountId = idOrNull();
        if (accountId == null) {
            throw BusinessException.unauthorized("请先登录");
        }
        return accountId;
    }
}
