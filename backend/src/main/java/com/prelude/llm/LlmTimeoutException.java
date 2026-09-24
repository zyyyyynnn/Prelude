package com.prelude.llm;

import com.prelude.BusinessException;

public class LlmTimeoutException extends BusinessException {

    public LlmTimeoutException(String message) {
        super(504, "llm_timeout", message);
    }
}
