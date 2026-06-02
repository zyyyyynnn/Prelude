package com.interview.common;

public class LlmTimeoutException extends BusinessException {

    public LlmTimeoutException(String message) {
        super(504, message);
    }
}
