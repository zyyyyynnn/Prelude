package com.prelude.identity.api;

import com.prelude.Result;
import com.prelude.identity.Account;
import com.prelude.identity.AccountMapper;
import com.prelude.identity.AccountPrincipal;
import com.prelude.identity.application.AuthenticationService;
import com.prelude.identity.application.OAuthLoginService;
import com.prelude.identity.application.PendingOAuthBinding;
import com.prelude.identity.application.SessionRevokeService;
import com.prelude.identity.infrastructure.SessionAuthentication;
import com.prelude.BusinessException;
import com.prelude.identity.api.CurrentUserResponse;
import com.prelude.identity.api.SessionView;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthenticationService authenticationService;
    private final SessionRevokeService sessionRevokeService;
    private final AccountMapper accountMapper;
    private final SecurityContextRepository securityContextRepository;
    private final SessionAuthenticationStrategy sessionAuthenticationStrategy;

    @PostMapping("/register")
    public Result<Void> register(@Valid @RequestBody RegisterRequest request) {
        authenticationService.register(request);
        return Result.success();
    }

    @PostMapping("/login")
    public Result<LoginResponse> login(
        @Valid @RequestBody LoginRequest request,
        HttpServletRequest servletRequest,
        HttpServletResponse servletResponse
    ) {
        PendingOAuthBinding pending = extractPendingBinding(servletRequest);
        AccountPrincipal principal = authenticationService.login(request, pending);
        SessionAuthentication.establish(
            principal, securityContextRepository, sessionAuthenticationStrategy,
            servletRequest, servletResponse);
        return Result.success(new LoginResponse(principal.accountId()));
    }

    @GetMapping("/me")
    public Result<CurrentUserResponse> me() {
        long accountId = accountId();
        Account account = accountMapper.selectById(accountId);
        if (account == null) {
            throw BusinessException.unauthorized("请先登录");
        }
        return Result.success(new CurrentUserResponse(account.getId(), account.getUsername()));
    }

    @PostMapping("/logout")
    public Result<Void> logout(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        SecurityContextHolder.clearContext();
        return Result.success();
    }

    @GetMapping("/sessions")
    public Result<List<SessionView>> sessions(HttpServletRequest request) {
        return Result.success(sessionRevokeService.listCurrentAccountSessions(request.getSession(false)));
    }

    @DeleteMapping("/sessions/{sessionId}")
    public Result<Void> revokeSession(@PathVariable String sessionId) {
        sessionRevokeService.revoke(sessionId);
        return Result.success();
    }

    private PendingOAuthBinding extractPendingBinding(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            return null;
        }
        Object attribute = session.getAttribute(OAuthLoginService.PENDING_ATTRIBUTE);
        return attribute instanceof PendingOAuthBinding pending ? pending : null;
    }

    private long accountId() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof AccountPrincipal principal) {
            return principal.accountId();
        }
        throw BusinessException.unauthorized("请先登录");
    }
}
