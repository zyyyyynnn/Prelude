package com.prelude;

import java.net.URI;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Single problem-details writer for the API.
 *
 * <p>Extending {@link ResponseEntityExceptionHandler} is what keeps request-level failures
 * (unreadable body, wrong type, wrong method) on their own protocol status; without it the
 * catch-all below would report every one of them as a server error.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Map<Class<?>, String> REQUEST_ERROR_CODES = Map.of(
        HttpMessageNotReadableException.class, "malformed_request",
        MethodArgumentTypeMismatchException.class, "malformed_request",
        HttpMediaTypeNotSupportedException.class, "unsupported_media_type",
        HttpRequestMethodNotSupportedException.class, "method_not_allowed",
        MethodArgumentNotValidException.class, "validation_failed",
        HandlerMethodValidationException.class, "validation_failed",
        NoHandlerFoundException.class, "resource_not_found",
        NoResourceFoundException.class, "resource_not_found"
    );

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ProblemDetail> handleBusinessException(BusinessException exception) {
        HttpStatus status = HttpStatus.valueOf(exception.getStatus());
        return ResponseEntity.status(status)
            .body(problemDetail(status, exception.getCode(), exception.getMessage()));
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
        MethodArgumentNotValidException exception,
        HttpHeaders headers,
        HttpStatusCode status,
        WebRequest request
    ) {
        FieldError fieldError = exception.getBindingResult().getFieldError();
        String message = fieldError == null ? "请求参数不合法" : fieldError.getDefaultMessage();
        return ResponseEntity.status(status)
            .body(problemDetail(HttpStatus.valueOf(status.value()), "validation_failed", message));
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
        Exception exception,
        Object body,
        HttpHeaders headers,
        HttpStatusCode status,
        WebRequest request
    ) {
        HttpStatus resolved = HttpStatus.valueOf(status.value());
        if (resolved.is5xxServerError()) {
            log.error("Unhandled server exception", exception);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(problemDetail(HttpStatus.INTERNAL_SERVER_ERROR, "internal_error", "服务器内部错误"));
        }
        return ResponseEntity.status(resolved)
            .body(problemDetail(resolved, codeFor(exception), detailOf(body, exception)));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleException(Exception exception) {
        log.error("Unhandled server exception", exception);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(problemDetail(HttpStatus.INTERNAL_SERVER_ERROR, "internal_error", "服务器内部错误"));
    }

    private static String codeFor(Exception exception) {
        return REQUEST_ERROR_CODES.entrySet().stream()
            .filter(entry -> entry.getKey().isInstance(exception))
            .map(Map.Entry::getValue)
            .findFirst()
            .orElse("request_rejected");
    }

    private static String detailOf(Object body, Exception exception) {
        if (body instanceof ProblemDetail problem && problem.getDetail() != null) {
            return problem.getDetail();
        }
        return exception.getMessage() == null ? "请求无法处理" : exception.getMessage();
    }

    static ProblemDetail problemDetail(HttpStatus status, String code, String message) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, message);
        problem.setType(URI.create("about:blank"));
        problem.setProperty("code", code);
        return problem;
    }
}
