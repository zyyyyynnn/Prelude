package com.prelude.llm;

import com.prelude.BusinessException;

public class LlmServerException extends BusinessException {

    public LlmServerException(String message) {
        super(502, "llm_server_error", message);
    }
}
