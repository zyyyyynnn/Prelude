package com.prelude;

public class LlmTimeoutException extends BusinessException {

    public LlmTimeoutException(String message) {
        super(504, "llm_timeout", message);
    }
}
