package com.prelude.llm;

import com.prelude.BusinessException;
import okhttp3.Dns;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CustomLlmEgressPolicyTest {

    @Test
    void rejectsCustomEndpointWhenDnsResolvesToPrivateAddress() throws Exception {
        Dns privateDns = hostname -> List.of(InetAddress.getByName("127.0.0.1"));
        CustomLlmEgressPolicy policy = new CustomLlmEgressPolicy(
            false,
            false,
            Set.of(443),
            privateDns
        );

        assertThatThrownBy(() -> policy.validateConfiguredEndpoint("https://models.example.com"))
            .isInstanceOf(BusinessException.class)
            .hasMessage("Base URL 域名无法解析或解析结果不安全");
    }

    @Test
    void runtimeClientRejectsDnsRebindingAfterConfigurationValidation() throws Exception {
        AtomicInteger lookups = new AtomicInteger();
        Dns rebindingDns = hostname -> {
            if (lookups.getAndIncrement() == 0) {
                return List.of(InetAddress.getByName("93.184.216.34"));
            }
            return List.of(InetAddress.getByName("127.0.0.1"));
        };
        CustomLlmEgressPolicy policy = new CustomLlmEgressPolicy(
            false,
            false,
            Set.of(443),
            rebindingDns
        );

        policy.validateConfiguredEndpoint("https://models.example.com");
        EgressHttpClientFactory factory = new EgressHttpClientFactory(policy);
        okhttp3.Request request = new okhttp3.Request.Builder()
            .url("https://models.example.com/v1/chat/completions")
            .build();

        assertThatThrownBy(() -> factory.runtimeClient().newCall(request).execute())
            .isInstanceOf(UnknownHostException.class)
            .hasMessageContaining("Blocked non-public address");
    }

    @Test
    void discoveryClientUsesTheSamePerLookupDnsGuard() throws Exception {
        AtomicInteger lookups = new AtomicInteger();
        Dns rebindingDns = hostname -> {
            if (lookups.getAndIncrement() == 0) {
                return List.of(InetAddress.getByName("93.184.216.34"));
            }
            return List.of(InetAddress.getByName("10.0.0.8"));
        };
        CustomLlmEgressPolicy policy = new CustomLlmEgressPolicy(
            false,
            false,
            Set.of(443),
            rebindingDns
        );

        policy.validateConfiguredEndpoint("https://models.example.com");
        EgressHttpClientFactory factory = new EgressHttpClientFactory(policy);
        okhttp3.Request request = new okhttp3.Request.Builder()
            .url("https://models.example.com/models")
            .build();

        assertThatThrownBy(() -> factory.discoveryClient().newCall(request).execute())
            .isInstanceOf(UnknownHostException.class)
            .hasMessageContaining("Blocked non-public address");
    }
}
