package com.prelude;

import lombok.Getter;

/**
 * Application-level failure with a stable API code. The status is a plain HTTP code so
 * use cases never import the servlet stack; {@code GlobalExceptionHandler} is the only
 * place that turns it into {@code HttpStatus}.
 */
@Getter
public class BusinessException extends RuntimeException {

    private final int status;
    private final String code;

    public BusinessException(int status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public static BusinessException badRequest(String message) {
        return badRequest("bad_request", message);
    }

    public static BusinessException badRequest(String code, String message) {
        return new BusinessException(400, code, message);
    }

    public static BusinessException unauthorized(String message) {
        return new BusinessException(401, "authentication_required", message);
    }

    public static BusinessException invalidCredentials(String message) {
        return new BusinessException(401, "invalid_credentials", message);
    }

    public static BusinessException permissionDenied(String message) {
        return new BusinessException(403, "permission_denied", message);
    }

    public static BusinessException notFound(String message) {
        return notFound("not_found", message);
    }

    public static BusinessException notFound(String code, String message) {
        return new BusinessException(404, code, message);
    }

    public static BusinessException conflict(String code, String message) {
        return new BusinessException(409, code, message);
    }

    public static BusinessException revisionConflict(String message) {
        return conflict("revision_conflict", message);
    }

    public static BusinessException rateLimited(String message) {
        return new BusinessException(429, "rate_limited", message);
    }

    public static BusinessException avatarUnreadable(String message) {
        return badRequest("avatar_unreadable", message);
    }

    public static BusinessException oauthBindingConflict(String message) {
        return conflict("oauth_binding_conflict", message);
    }
}
