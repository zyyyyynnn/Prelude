package com.prelude;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class BusinessException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    public BusinessException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public static BusinessException badRequest(String message) {
        return new BusinessException(HttpStatus.BAD_REQUEST, "bad_request", message);
    }

    public static BusinessException unauthorized(String message) {
        return new BusinessException(HttpStatus.UNAUTHORIZED, "authentication_required", message);
    }

    public static BusinessException invalidCredentials(String message) {
        return new BusinessException(HttpStatus.UNAUTHORIZED, "invalid_credentials", message);
    }

    public static BusinessException permissionDenied(String message) {
        return new BusinessException(HttpStatus.FORBIDDEN, "permission_denied", message);
    }

    public static BusinessException notFound(String message) {
        return new BusinessException(HttpStatus.NOT_FOUND, "not_found", message);
    }

    public static BusinessException revisionConflict(String message) {
        return new BusinessException(HttpStatus.CONFLICT, "revision_conflict", message);
    }
}
