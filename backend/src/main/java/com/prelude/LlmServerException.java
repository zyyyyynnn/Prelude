package com.prelude;

public class LlmServerException extends BusinessException {

    public LlmServerException(String message) {
        super(500, message);
    }
}
