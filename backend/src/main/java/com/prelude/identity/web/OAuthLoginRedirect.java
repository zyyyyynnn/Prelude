package com.prelude.identity.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.util.StringUtils;

import java.util.Set;

final class OAuthLoginRedirect {

    static final String SESSION_ATTRIBUTE = OAuthLoginRedirect.class.getName();
    private static final String DEFAULT_REDIRECT = "/interview";
    private static final Set<String> ALLOWED_PATHS = Set.of("/", "/interview", "/analytics");

    private OAuthLoginRedirect() {
    }

    static void remember(HttpServletRequest request, String redirect) {
        request.getSession(true).setAttribute(SESSION_ATTRIBUTE, sanitize(redirect));
    }

    static String consume(HttpServletRequest request) {
        var session = request.getSession(false);
        if (session == null) {
            return DEFAULT_REDIRECT;
        }
        Object value = session.getAttribute(SESSION_ATTRIBUTE);
        session.removeAttribute(SESSION_ATTRIBUTE);
        return value instanceof String redirect ? sanitize(redirect) : DEFAULT_REDIRECT;
    }

    static String sanitize(String redirect) {
        if (!StringUtils.hasText(redirect) || redirect.length() > 2048 || !redirect.startsWith("/")
            || redirect.startsWith("//") || redirect.contains("\\")
            || redirect.chars().anyMatch(character -> character < 0x20 || character == 0x7f)) {
            return DEFAULT_REDIRECT;
        }
        int queryStart = redirect.indexOf('?');
        int fragmentStart = redirect.indexOf('#');
        int pathEnd = redirect.length();
        if (queryStart >= 0) pathEnd = Math.min(pathEnd, queryStart);
        if (fragmentStart >= 0) pathEnd = Math.min(pathEnd, fragmentStart);
        return ALLOWED_PATHS.contains(redirect.substring(0, pathEnd)) ? redirect : DEFAULT_REDIRECT;
    }
}
