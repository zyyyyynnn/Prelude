package com.prelude.identity.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.prelude.BusinessException;
import com.prelude.identity.Account;
import com.prelude.identity.AccountMapper;
import com.prelude.identity.AccountPrincipal;
import com.prelude.identity.OAuthBinding;
import com.prelude.identity.OAuthBindingMapper;
import com.prelude.identity.api.LoginRequest;
import com.prelude.identity.api.RegisterRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthenticationService {

    private final AccountMapper accountMapper;
    private final OAuthBindingMapper oauthBindingMapper;
    private final PasswordEncoder passwordEncoder;

    @Transactional(rollbackFor = Exception.class)
    public void register(RegisterRequest request) {
        long count = accountMapper.selectCount(new LambdaQueryWrapper<Account>()
            .eq(Account::getUsername, request.getUsername()));
        if (count > 0) {
            throw BusinessException.badRequest("用户名已存在");
        }

        Account account = new Account();
        account.setUsername(request.getUsername());
        account.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        account.setEmail(request.getEmail());
        account.setRevision(0L);
        accountMapper.insert(account);
    }

    public AccountPrincipal login(LoginRequest request, PendingOAuthBinding pending) {
        Account account = accountMapper.selectOne(new LambdaQueryWrapper<Account>()
            .eq(Account::getUsername, request.getUsername())
            .last("LIMIT 1"));
        if (account == null
            || account.getPasswordHash() == null
            || !passwordEncoder.matches(request.getPassword(), account.getPasswordHash())) {
            throw BusinessException.invalidCredentials("用户名或密码错误");
        }
        if (pending != null && account.getEmail() != null
            && pending.verifiedEmail().equalsIgnoreCase(account.getEmail())) {
            createBinding(pending.provider(), pending.providerSubject(), account.getId());
        }
        return new AccountPrincipal(account.getId(), account.getUsername());
    }

    void createBinding(String provider, String providerSubject, Long accountId) {
        OAuthBinding binding = new OAuthBinding();
        binding.setAccountId(accountId);
        binding.setProvider(provider);
        binding.setProviderSubject(providerSubject);
        try {
            oauthBindingMapper.insert(binding);
        } catch (DuplicateKeyException duplicate) {
            // The binding already exists; completing login idempotently is the correct outcome.
        }
    }
}
