package com.prelude;

public class LlmServerException extends BusinessException {

    public LlmServerException(String message) {
        super(502, "llm_server_error", message);
    }
}
