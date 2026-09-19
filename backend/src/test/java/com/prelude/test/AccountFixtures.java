package com.prelude.test;

import com.prelude.identity.domain.Account;
import com.prelude.identity.api.port.AccountRepository;
import com.prelude.identity.AccountPrincipal;
import com.prelude.identity.api.AvatarStoragePort;
import com.prelude.identity.api.CurrentAccount;
import com.prelude.identity.api.LoginRequest;
import com.prelude.identity.api.RegisterRequest;
import com.prelude.identity.api.UserProfileResponse;
import com.prelude.identity.application.AuthenticationService;
import com.prelude.identity.application.OAuthLoginService;
import com.prelude.identity.application.PendingOAuthBinding;
import com.prelude.identity.infrastructure.OAuthVerifiedEmailResolver;
import com.prelude.identity.infrastructure.ProviderIdentityUser;
import org.mockito.Mockito;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;
import java.util.UUID;

public final class AccountFixtures {

    public static final String PENDING_ATTRIBUTE = OAuthLoginService.PENDING_ATTRIBUTE;

    private AccountFixtures() {
    }

    public static long create(JdbcTemplate jdbcTemplate, String prefix) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                "INSERT INTO user_account (username, revision) VALUES (?, 0)",
                Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, prefix + "-" + UUID.randomUUID());
            return ps;
        }, keyHolder);
        Number key = keyHolder.getKey();
        return key == null ? 0L : key.longValue();
    }

    public static void authenticate(long accountId) {
        AccountPrincipal principal = new AccountPrincipal(accountId, "tester");
        SecurityContextHolder.getContext().setAuthentication(
            UsernamePasswordAuthenticationToken.authenticated(
                principal, null, List.of()));
    }

    public static CurrentAccount current(long accountId) {
        CurrentAccount currentAccount = Mockito.mock(CurrentAccount.class);
        Mockito.when(currentAccount.requireId()).thenReturn(accountId);
        return currentAccount;
    }

    public static AuthenticationService authenticationService(
        AccountRepository accounts, OAuthLoginService oauthLoginService, PasswordEncoder passwordEncoder) {
        return new AuthenticationService(accounts, oauthLoginService, passwordEncoder);
    }

    public static RegisterRequest registerRequest(String username, String password) {
        RegisterRequest request = new RegisterRequest();
        request.setUsername(username);
        request.setPassword(password);
        return request;
    }

    public static LoginRequest loginRequest(String username, String password) {
        LoginRequest request = new LoginRequest();
        request.setUsername(username);
        request.setPassword(password);
        return request;
    }

    public static PendingOAuthBinding pendingOAuthBinding(String provider, String subject, String email) {
        return new PendingOAuthBinding(provider, subject, email);
    }

    public static OAuthLoginService mockOAuthLoginService() {
        return Mockito.mock(OAuthLoginService.class);
    }

    public static AccountPrincipal principal(long accountId, String username) {
        return new AccountPrincipal(accountId, username);
    }

    public static ProviderIdentityUser providerIdentityUser(OAuth2User user, OAuth2AccessToken token) {
        return new ProviderIdentityUser(user, token);
    }

    public static OAuthVerifiedEmailResolver mockEmailResolver() {
        return Mockito.mock(OAuthVerifiedEmailResolver.class);
    }

    public static void whenEmailResolved(
        OAuthVerifiedEmailResolver resolver, OAuth2AccessToken token, String email) {
        Mockito.when(resolver.resolveVerifiedEmail(Mockito.any(ProviderIdentityUser.class), Mockito.eq(token)))
            .thenReturn(email);
    }

    public static Account account(Long id, String username, Long revision, String avatarUrl, String passwordHash, String email) {
        Account acc = new Account();
        acc.setId(id);
        acc.setUsername(username);
        acc.setRevision(revision);
        acc.setAvatarUrl(avatarUrl);
        acc.setPasswordHash(passwordHash);
        acc.setEmail(email);
        return acc;
    }

    public static AccountRepository mockAccountRepository() {
        return Mockito.mock(AccountRepository.class);
    }

    public static AvatarStoragePort mockAvatarStoragePort() {
        return Mockito.mock(AvatarStoragePort.class);
    }

    public static void whenStagedAvatar(AvatarStoragePort port, long accountId, String mimeType, byte[] bytes, String resultUrl) {
        Mockito.when(port.stage(accountId, mimeType, bytes)).thenReturn(resultUrl);
    }

    public static void verifyAvatarDiscarded(AvatarStoragePort port, long accountId, String avatarUrl) {
        Mockito.verify(port).discard(accountId, avatarUrl);
    }

    public static void verifyAvatarNeverDiscarded(AvatarStoragePort port, long accountId, String avatarUrl) {
        Mockito.verify(port, Mockito.never()).discard(accountId, avatarUrl);
    }

    public static String userProfileAvatar(UserProfileResponse response) {
        return response.avatarUrl();
    }
}
