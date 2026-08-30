package com.prelude.identity.application;

import com.prelude.BusinessException;
import com.prelude.identity.AccountPrincipal;
import com.prelude.identity.api.CurrentAccount;
import com.prelude.identity.api.SessionView;
import lombok.RequiredArgsConstructor;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class SessionRevokeService {

    private final FindByIndexNameSessionRepository<? extends Session> sessionRepository;
    private final CurrentAccount currentAccount;

    public List<SessionView> listCurrentAccountSessions(jakarta.servlet.http.HttpSession currentSession) {
        Map<String, ? extends Session> sessions =
            sessionRepository.findByPrincipalName(principalName());
        return sessions.values().stream()
            .map(session -> new SessionView(
                session.getId(),
                currentSession != null && session.getId().equals(currentSession.getId()),
                session.getLastAccessedTime()
            ))
            .sorted(Comparator.comparing(SessionView::lastAccessedAt).reversed())
            .toList();
    }

    public void revoke(String sessionId) {
        long accountId = currentAccount.requireId();
        Session session = sessionRepository.findById(sessionId);
        if (session == null || !isOwnedBy(session, accountId)) {
            throw BusinessException.notFound("会话不存在");
        }
        sessionRepository.deleteById(sessionId);
    }

    private boolean isOwnedBy(Session session, long accountId) {
        Object context = session.getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
        return context instanceof org.springframework.security.core.context.SecurityContext securityContext
            && securityContext.getAuthentication() != null
            && securityContext.getAuthentication().getPrincipal() instanceof AccountPrincipal principal
            && principal.accountId() == accountId;
    }

    private String principalName() {
        return String.valueOf(currentAccount.requireId());
    }
}
