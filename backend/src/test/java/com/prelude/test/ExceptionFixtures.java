package com.prelude.test;

import com.prelude.BusinessException;
import com.prelude.LlmServerException;
import org.assertj.core.api.AbstractThrowableAssert;
import org.assertj.core.api.ThrowableAssert;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

public final class ExceptionFixtures {

    private ExceptionFixtures() {
    }

    public static AbstractThrowableAssert<?, ? extends Throwable> assertBusinessException(
            ThrowableAssert.ThrowingCallable callable) {
        return assertThatThrownBy(callable).isInstanceOf(BusinessException.class);
    }

    public static AbstractThrowableAssert<?, ? extends Throwable> assertBusinessException(
            ThrowableAssert.ThrowingCallable callable, String code) {
        return assertThatThrownBy(callable)
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("code", code);
    }

    public static AbstractThrowableAssert<?, ? extends Throwable> assertBusinessExceptionMessage(
            ThrowableAssert.ThrowingCallable callable, String message) {
        return assertThatThrownBy(callable)
            .isInstanceOf(BusinessException.class)
            .hasMessage(message);
    }

    public static AbstractThrowableAssert<?, ? extends Throwable> assertLlmServerException(
            ThrowableAssert.ThrowingCallable callable) {
        return assertThatThrownBy(callable).isInstanceOf(LlmServerException.class);
    }

    public static BusinessException revisionConflict(String message) {
        return BusinessException.revisionConflict(message);
    }
}
