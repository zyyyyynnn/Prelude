package com.prelude;

public class LlmTimeoutException extends BusinessException {

    public LlmTimeoutException(String message) {
        super(504, message);
    }
}
