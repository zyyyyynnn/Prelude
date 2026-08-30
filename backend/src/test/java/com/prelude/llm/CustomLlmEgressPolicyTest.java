package com.prelude.llm;

import com.prelude.BusinessException;
import okhttp3.Dns;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.util.List;
import java.util.Set;

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
}
