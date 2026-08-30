package com.prelude;

public class LlmServerException extends BusinessException {

    public LlmServerException(String message) {
        super(org.springframework.http.HttpStatus.BAD_GATEWAY, "llm_server_error", message);
    }
}
