package com.prelude.llm;

import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Builds OkHttp clients that enforce custom-endpoint egress rules.
 * Spring AI's client accepts interceptors, so guarded DNS and the no-redirect
 * policy are applied here — the only custom-endpoint HTTP customization needed.
 */
@Component
public class EgressHttpClientFactory {

    private final CustomLlmEgressPolicy egressPolicy;

    public EgressHttpClientFactory(CustomLlmEgressPolicy egressPolicy) {
        this.egressPolicy = egressPolicy;
    }

    public okhttp3.OkHttpClient runtimeClient() {
        return configuredBuilder()
            .readTimeout(Duration.ofSeconds(60))
            .writeTimeout(Duration.ofSeconds(60))
            .callTimeout(Duration.ofSeconds(90))
            .build();
    }

    /**
     * OkHttp client for direct custom-endpoint calls (model discovery) with
     * the same egress constraints.
     */
    public okhttp3.OkHttpClient discoveryClient() {
        return configuredBuilder()
            .readTimeout(Duration.ofSeconds(15))
            .writeTimeout(Duration.ofSeconds(15))
            .callTimeout(Duration.ofSeconds(30))
            .build();
    }

    private okhttp3.OkHttpClient.Builder configuredBuilder() {
        return new okhttp3.OkHttpClient.Builder()
            .dns(egressPolicy::guardedLookup)
            .followRedirects(false)
            .followSslRedirects(false)
            .connectTimeout(Duration.ofSeconds(15))
            .addInterceptor(chain -> {
                egressPolicy.validateUrl(chain.request().url());
                return chain.proceed(chain.request());
            });
    }
}
