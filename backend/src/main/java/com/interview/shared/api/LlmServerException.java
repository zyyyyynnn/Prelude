package com.interview.shared.api;

public class LlmServerException extends BusinessException {

    public LlmServerException(String message) {
        super(500, message);
    }
}
