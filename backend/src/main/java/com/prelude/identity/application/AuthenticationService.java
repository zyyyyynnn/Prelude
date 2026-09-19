package com.prelude.identity.application;

import com.prelude.BusinessException;
import com.prelude.identity.AccountPrincipal;
import com.prelude.identity.api.LoginRequest;
import com.prelude.identity.api.RegisterRequest;
import com.prelude.identity.api.port.AccountRepository;
import com.prelude.identity.domain.Account;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthenticationService {

    private final AccountRepository accounts;
    private final OAuthLoginService oauthLoginService;
    private final PasswordEncoder passwordEncoder;

    @Transactional(rollbackFor = Exception.class)
    public void register(RegisterRequest request) {
        if (accounts.isUsernameTaken(request.getUsername())) {
            throw BusinessException.badRequest("用户名已存在");
        }

        Account account = new Account();
        account.setUsername(request.getUsername());
        account.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        account.setEmail(request.getEmail());
        account.setRevision(0L);
        try {
            accounts.add(account);
        } catch (DuplicateKeyException exception) {
            // The check above is only a fast path; concurrent registrations of the same username
            // reach the unique index, which is the actual authority.
            throw BusinessException.badRequest("用户名已存在");
        }
    }

    public AccountPrincipal login(LoginRequest request, PendingOAuthBinding pending, HttpSession session) {
        Account account = accounts.findByUsername(request.getUsername());
        if (account == null
            || account.getPasswordHash() == null
            || !passwordEncoder.matches(request.getPassword(), account.getPasswordHash())) {
            throw BusinessException.invalidCredentials("用户名或密码错误");
        }
        if (pending != null && account.getEmail() != null
            && pending.verifiedEmail().equalsIgnoreCase(account.getEmail())) {
            oauthLoginService.createBindingExact(pending.provider(), pending.providerSubject(), account.getId());
            // One-shot: the completed intent must not survive the rotated session.
            session.removeAttribute(OAuthLoginService.PENDING_ATTRIBUTE);
        }
        return new AccountPrincipal(account.getId(), account.getUsername());
    }
}
