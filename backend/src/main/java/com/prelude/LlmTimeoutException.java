package com.prelude;

public class LlmTimeoutException extends BusinessException {

    public LlmTimeoutException(String message) {
        super(org.springframework.http.HttpStatus.GATEWAY_TIMEOUT, "llm_timeout", message);
    }
}
