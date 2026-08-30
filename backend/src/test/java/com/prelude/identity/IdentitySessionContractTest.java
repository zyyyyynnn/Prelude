package com.prelude.identity;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Session authentication contract against real MySQL, Redis and Argon2id.
 */
@EnabledIfEnvironmentVariable(named = "PRELUDE_IDENTITY_SMOKE", matches = "true")
@SpringBootTest
@AutoConfigureMockMvc
class IdentitySessionContractTest {

    private static final Pattern CSRF_COOKIE = Pattern.compile("XSRF-TOKEN=([^;]+)");

    @Autowired
    private MockMvc mockMvc;

    @Test
    void registrationStoresArgon2idAndSessionAuthenticatesWithRotation() throws Exception {
        String username = unique("contract");
        registerAccount(username);

        MvcResult bootstrap = mockMvc.perform(get("/api/auth/me"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("authentication_required"))
            .andReturn();
        Cookie csrf = csrfCookie(bootstrap);

        MvcResult login = mockMvc.perform(post("/api/auth/login")
                .with(requestBody("""
                    {"username":"%s","password":"correct-horse"}
                    """.formatted(username)))
                .cookie(csrf)
                .header("X-XSRF-TOKEN", csrf.getValue()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.accountId").isNumber())
            .andReturn();
        Cookie firstSession = sessionCookie(login);

        // A second login inside the same session rotates the session id (fixation protection).
        MvcResult rotation = mockMvc.perform(post("/api/auth/login")
                .with(requestBody("""
                    {"username":"%s","password":"correct-horse"}
                    """.formatted(username)))
                .cookie(csrf, firstSession)
                .header("X-XSRF-TOKEN", csrf.getValue()))
            .andExpect(status().isOk())
            .andReturn();
        Cookie rotatedSession = sessionCookie(rotation);
        assertThat(rotatedSession.getValue()).isNotEqualTo(firstSession.getValue());

        mockMvc.perform(get("/api/auth/me").cookie(csrf, rotatedSession))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.username").value(username));

        MvcResult logout = mockMvc.perform(post("/api/auth/logout")
                .cookie(csrf, rotatedSession)
                .header("X-XSRF-TOKEN", csrf.getValue()))
            .andExpect(status().isOk())
            .andReturn();
        Cookie logoutSession = sessionCookie(logout);
        mockMvc.perform(get("/api/auth/me").cookie(csrf, logoutSession))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void wrongPasswordIsAnInvalidCredentialsProblem() throws Exception {
        String username = unique("wrongpass");
        Cookie csrf = registerAccount(username);

        mockMvc.perform(post("/api/auth/login")
                .with(requestBody("""
                    {"username":"%s","password":"totally-wrong"}
                    """.formatted(username)))
                .cookie(csrf)
                .header("X-XSRF-TOKEN", csrf.getValue()))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("invalid_credentials"));
    }

    @Test
    void unsafeRequestsWithoutCsrfTokenAreRejected() throws Exception {
        String username = unique("csrf");
        registerAccount(username);

        mockMvc.perform(post("/api/auth/login")
                .with(requestBody("""
                    {"username":"%s","password":"correct-horse"}
                    """.formatted(username))))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("csrf_failure"));
    }

    @Test
    void untrustedOriginsAreRejected() throws Exception {
        String username = unique("origin");
        Cookie csrf = registerAccount(username);

        mockMvc.perform(post("/api/auth/login")
                .with(requestBody("""
                    {"username":"%s","password":"correct-horse"}
                    """.formatted(username)))
                .header("Origin", "https://evil.example")
                .cookie(csrf)
                .header("X-XSRF-TOKEN", csrf.getValue()))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("origin_rejected"));
    }

    @Test
    void profileMutationsEnforceRevisionAndOperationIdContract() throws Exception {
        String username = unique("profile");
        Cookie[] auth = authenticatedSession(username);

        MvcResult profileResult = mockMvc.perform(get("/api/user/profile").cookie(auth))
            .andExpect(status().isOk())
            .andReturn();
        long revision = Long.parseLong(com.jayway.jsonpath.JsonPath.read(
            profileResult.getResponse().getContentAsString(), "$.data.revision").toString());

        String operationId = UUID.randomUUID().toString();
        String csrfToken = auth[0].getValue();
        MvcResult updated = mockMvc.perform(put("/api/user/profile")
                .cookie(auth)
                .header("X-XSRF-TOKEN", csrfToken)
                .contentType("application/json")
                .content("""
                    {"username":"%s","expectedRevision":%d,"operationId":"%s"}
                    """.formatted(username + "-x", revision, operationId)))
            .andExpect(status().isOk())
            .andReturn();
        long nextRevision = Long.parseLong(com.jayway.jsonpath.JsonPath.read(
            updated.getResponse().getContentAsString(), "$.data.revision").toString());
        assertThat(nextRevision).isEqualTo(revision + 1);

        // Duplicate operationId replays the authoritative projection without a new mutation.
        mockMvc.perform(put("/api/user/profile")
                .cookie(auth)
                .header("X-XSRF-TOKEN", csrfToken)
                .contentType("application/json")
                .content("""
                    {"username":"%s","expectedRevision":%d,"operationId":"%s"}
                    """.formatted(username + "-y", revision, operationId)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.revision").value(nextRevision));

        // Stale revision conflicts deterministically.
        mockMvc.perform(put("/api/user/profile")
                .cookie(auth)
                .header("X-XSRF-TOKEN", csrfToken)
                .contentType("application/json")
                .content("""
                    {"username":"%s","expectedRevision":%d,"operationId":"%s"}
                    """.formatted(username + "-z", revision, UUID.randomUUID())))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("revision_conflict"));
    }

    @Test
    void accountACannotRevokeAccountBSession() throws Exception {
        String accountA = unique("revoke-a");
        String accountB = unique("revoke-b");
        Cookie[] sessionA = authenticatedSession(accountA);
        Cookie[] sessionB = authenticatedSession(accountB);

        MvcResult list = mockMvc.perform(get("/api/auth/sessions").cookie(sessionA))
            .andExpect(status().isOk())
            .andReturn();
        String sessionId = com.jayway.jsonpath.JsonPath.read(
            list.getResponse().getContentAsString(), "$.data[0].sessionId");

        mockMvc.perform(delete("/api/auth/sessions/" + sessionId)
                .cookie(sessionB)
                .header("X-XSRF-TOKEN", sessionB[0].getValue()))
            .andExpect(status().isNotFound());

        mockMvc.perform(delete("/api/auth/sessions/" + sessionId)
                .cookie(sessionA)
                .header("X-XSRF-TOKEN", sessionA[0].getValue()))
            .andExpect(status().isOk());
        mockMvc.perform(get("/api/auth/me").cookie(sessionA))
            .andExpect(status().isUnauthorized());
    }

    /** Registration is an unsafe request, so it exercises the real CSRF bootstrap contract. */
    private Cookie registerAccount(String username) throws Exception {
        Cookie csrf = csrfCookie(mockMvc.perform(get("/api/health")).andReturn());
        mockMvc.perform(post("/api/auth/register")
                .with(requestBody("""
                    {"username":"%s","password":"correct-horse"}
                    """.formatted(username)))
                .cookie(csrf)
                .header("X-XSRF-TOKEN", csrf.getValue()))
            .andExpect(status().isOk());
        return csrf;
    }

    private Cookie[] authenticatedSession(String username) throws Exception {
        Cookie csrf = registerAccount(username);
        MvcResult login = mockMvc.perform(post("/api/auth/login")
                .with(requestBody("""
                    {"username":"%s","password":"correct-horse"}
                    """.formatted(username)))
                .cookie(csrf)
                .header("X-XSRF-TOKEN", csrf.getValue()))
            .andExpect(status().isOk())
            .andReturn();
        return new Cookie[]{csrf, sessionCookie(login)};
    }

    private Cookie csrfCookie(MvcResult result) {
        for (String header : result.getResponse().getHeaders("Set-Cookie")) {
            Matcher matcher = CSRF_COOKIE.matcher(header);
            if (matcher.find()) {
                return new Cookie("XSRF-TOKEN", matcher.group(1));
            }
        }
        throw new AssertionError("Expected an XSRF-TOKEN cookie in the response");
    }

    private Cookie sessionCookie(MvcResult result) {
        for (String name : new String[] {"JSESSIONID", "SESSION"}) {
            for (String header : result.getResponse().getHeaders("Set-Cookie")) {
                if (header.startsWith(name + "=")) {
                    String value = header.substring(name.length() + 1, header.indexOf(';'));
                    return new Cookie(name, value);
                }
            }
        }
        throw new AssertionError("Expected a session cookie in the response");
    }

    private RequestPostProcessor requestBody(String body) {
        return request -> {
            request.setContentType("application/json");
            request.setContent(body.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return request;
        };
    }

    private String unique(String prefix) {
        return prefix + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }
}
