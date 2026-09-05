package com.prelude.llm;

import com.prelude.BusinessException;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import org.springframework.ai.retry.NonTransientAiException;
import org.springframework.ai.retry.TransientAiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

/**
 * Bounded retry owner for synchronous LLM transport calls. Provider SDK
 * retries are disabled; streaming keeps its first-visible-delta rule in
 * ModelExecutionService while sharing this failure classification.
 */
@Component
public class LlmTransportRetry {

    private final RetryRegistry retryRegistry;
    private final int maxAttempts;

    public LlmTransportRetry(
        @Value("${prelude.llm.transport-retry-max-attempts:3}") int maxAttempts
    ) {
        this.maxAttempts = Math.max(1, maxAttempts);
        RetryConfig retryConfig = RetryConfig.custom()
            .maxAttempts(this.maxAttempts)
            .intervalFunction(attempt -> Duration.ofSeconds(2L * attempt).toMillis())
            .retryOnException(this::isTransient)
            .failAfterMaxAttempts(true)
            .build();
        this.retryRegistry = RetryRegistry.of(retryConfig);
    }

    public <T> T execute(String operation, Supplier<T> supplier) {
        Retry retry = retryRegistry.retry(operation);
        return retry.executeSupplier(supplier);
    }

    public int maxAttempts() {
        return maxAttempts;
    }

    public boolean isTransient(Throwable failure) {
        for (Throwable current = failure; current != null; current = current.getCause()) {
            if (current instanceof NonTransientAiException || current instanceof BusinessException) {
                return false;
            }
            if (current instanceof TransientAiException
                || current instanceof com.openai.errors.OpenAIIoException
                || current instanceof com.anthropic.errors.AnthropicIoException
                || current instanceof SocketTimeoutException
                || current instanceof java.net.http.HttpTimeoutException
                || current instanceof TimeoutException
                || current instanceof ConnectException) {
                return true;
            }
            if (current instanceof com.openai.errors.OpenAIServiceException service) {
                return service.statusCode() == 429 || service.statusCode() >= 500;
            }
            if (current instanceof com.anthropic.errors.AnthropicServiceException service) {
                return service.statusCode() == 429 || service.statusCode() >= 500;
            }
        }
        return false;
    }

    public boolean isTimeout(Throwable failure) {
        for (Throwable current = failure; current != null; current = current.getCause()) {
            if (current instanceof SocketTimeoutException
                || current instanceof java.net.http.HttpTimeoutException
                || current instanceof TimeoutException) {
                return true;
            }
        }
        return false;
    }
}
