package com.interview.shared.api;

public class LlmTimeoutException extends BusinessException {

    public LlmTimeoutException(String message) {
        super(504, message);
    }
}
