package com.prelude.llm;

import okhttp3.Interceptor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Supplies egress constraints for the OpenAI-compatible custom endpoint
 * transport. Spring AI's OkHttp client accepts interceptors, so the guarded
 * DNS + no-redirect policy is applied there — the only custom-endpoint HTTP
 * customization this module needs.
 */
@Component
public class EgressHttpClientFactory {

    private final CustomLlmEgressPolicy egressPolicy;

    public EgressHttpClientFactory(CustomLlmEgressPolicy egressPolicy) {
        this.egressPolicy = egressPolicy;
    }

    /**
     * OkHttp interceptor enforcing the egress policy on every custom-endpoint
     * request: re-validated URL, guarded DNS, redirects refused.
     */
    public List<Interceptor> openAiInterceptors() {
        return List.of(chain -> {
            okhttp3.HttpUrl url = chain.request().url();
            egressPolicy.validateUrl(url);
            egressPolicy.guardedLookup(url.host());
            return chain.proceed(chain.request());
        });
    }

    /**
     * OkHttp client for direct custom-endpoint calls (model discovery) with
     * the same egress constraints.
     */
    public okhttp3.OkHttpClient discoveryClient() {
        return new okhttp3.OkHttpClient.Builder()
            .dns(egressPolicy::guardedLookup)
            .followRedirects(false)
            .followSslRedirects(false)
            .connectTimeout(java.time.Duration.ofSeconds(15))
            .readTimeout(java.time.Duration.ofSeconds(15))
            .build();
    }
}
