package com.prelude;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.csrf.CsrfException;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Writes Spring Security authentication, authorization and CSRF failures
 * using the same ProblemDetail JSON contract as the application error handler.
 */
public class SecurityProblemWriter implements AuthenticationEntryPoint, AccessDeniedHandler {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    static void write(HttpServletResponse response, int status, String code, String message) throws IOException {
        if (response.isCommitted()) {
            return;
        }
        response.setStatus(status);
        response.setContentType("application/problem+json");
        ProblemDetail problem = GlobalExceptionHandler.problemDetail(HttpStatus.valueOf(status), code, message);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", problem.getType().toString());
        body.put("title", problem.getTitle());
        body.put("status", problem.getStatus());
        body.put("detail", problem.getDetail());
        body.put("code", code);
        response.getWriter().write(OBJECT_MAPPER.writeValueAsString(body));
    }

    @Override
    public void commence(
        HttpServletRequest request,
        HttpServletResponse response,
        AuthenticationException authenticationException
    ) throws IOException {
        write(response, HttpServletResponse.SC_UNAUTHORIZED, "authentication_required", "请先登录");
    }

    @Override
    public void handle(
        HttpServletRequest request,
        HttpServletResponse response,
        AccessDeniedException accessDeniedException
    ) throws IOException {
        String code = accessDeniedException instanceof CsrfException ? "csrf_failure" : "permission_denied";
        String message = accessDeniedException instanceof CsrfException ? "CSRF 校验失败" : "没有访问权限";
        write(response, HttpServletResponse.SC_FORBIDDEN, code, message);
    }
}
