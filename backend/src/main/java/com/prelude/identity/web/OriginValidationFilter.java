package com.prelude.identity.web;

import com.prelude.SecurityProblemWriter;
import com.prelude.identity.api.security.OriginPolicy;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

public class OriginValidationFilter extends OncePerRequestFilter {

    private static final Set<String> UNSAFE_METHODS = Set.of("POST", "PUT", "PATCH", "DELETE");

    private final OriginPolicy originPolicy;

    public OriginValidationFilter(OriginPolicy originPolicy) {
        this.originPolicy = originPolicy;
    }

    @Override
    protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain filterChain
    ) throws ServletException, IOException {
        String origin = request.getHeader("Origin");
        if (origin != null && UNSAFE_METHODS.contains(request.getMethod())
            && !originPolicy.isAllowed(origin, new OriginPolicy.RequestOrigin(
                request.getScheme(), request.getServerName(), request.getServerPort()))) {
            SecurityProblemWriter.write(response, HttpServletResponse.SC_FORBIDDEN, "origin_rejected", "请求来源不被允许");
            return;
        }
        filterChain.doFilter(request, response);
    }
}
