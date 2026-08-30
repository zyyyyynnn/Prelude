package com.prelude.identity.infrastructure;

import com.prelude.identity.AccountPrincipal;
import com.prelude.identity.api.SessionValidity;
import lombok.RequiredArgsConstructor;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SpringSessionValidity implements SessionValidity {

    private final SessionRepository<? extends Session> sessionRepository;

    @Override
    public boolean isActive(String sessionId, long accountId) {
        if (sessionId == null) {
            return false;
        }
        Session session = sessionRepository.findById(sessionId);
        if (session == null || session.isExpired()) {
            return false;
        }
        Object context = session.getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
        return context instanceof org.springframework.security.core.context.SecurityContext securityContext
            && securityContext.getAuthentication() != null
            && securityContext.getAuthentication().getPrincipal() instanceof AccountPrincipal principal
            && principal.accountId() == accountId;
    }
}
