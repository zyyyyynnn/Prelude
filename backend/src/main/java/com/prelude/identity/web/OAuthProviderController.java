package com.prelude.identity.web;

import com.prelude.BusinessException;
import com.prelude.Result;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.List;
import java.util.stream.Stream;

@RestController
@RequestMapping("/api/auth/oauth")
@RequiredArgsConstructor
public class OAuthProviderController {

    private final ObjectProvider<ClientRegistrationRepository> clientRegistrations;

    @GetMapping("/providers")
    public Result<List<String>> providers() {
        ClientRegistrationRepository repository = clientRegistrations.getIfAvailable();
        if (repository == null) {
            return Result.success(List.of());
        }
        return Result.success(Stream.of("google", "github")
            .filter(provider -> repository.findByRegistrationId(provider) != null)
            .toList());
    }

    @GetMapping("/{provider}/start")
    public void start(
        @PathVariable String provider,
        @RequestParam(required = false) String redirect,
        HttpServletRequest request,
        HttpServletResponse response
    ) throws IOException {
        ClientRegistrationRepository repository = clientRegistrations.getIfAvailable();
        if (repository == null || repository.findByRegistrationId(provider) == null) {
            throw BusinessException.notFound("登录方式未启用");
        }
        OAuthLoginRedirect.remember(request, redirect);
        response.sendRedirect("/oauth2/authorization/" + provider);
    }
}
