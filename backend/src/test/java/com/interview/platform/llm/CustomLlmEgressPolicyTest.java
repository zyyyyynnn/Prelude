package com.interview.platform.llm;

import com.interview.shared.api.BusinessException;
import okhttp3.Dns;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CustomLlmEgressPolicyTest {

    @Test
    void productionDefaultsRejectUnsafeProtocolPortAndLoopback() {
        CustomLlmEgressPolicy policy = new CustomLlmEgressPolicy(
            false, false, Set.of(443), Dns.SYSTEM);

        assertThatThrownBy(() -> policy.validateConfiguredEndpoint("http://example.com/v1"))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("HTTPS");
        assertThatThrownBy(() -> policy.validateConfiguredEndpoint("https://example.com:8443/v1"))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("端口");
        assertThatThrownBy(() -> policy.validateConfiguredEndpoint("https://127.0.0.1/v1"))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("不安全");
        assertThatThrownBy(() -> policy.validateConfiguredEndpoint("https://[::1]/v1"))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("不安全");
        assertThatThrownBy(() -> policy.validateConfiguredEndpoint("https://2130706433/v1"))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("不安全");
        assertThatThrownBy(() -> policy.validateConfiguredEndpoint("https://user:secret@example.com/v1"))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("凭证");
        assertThatThrownBy(() -> policy.validateConfiguredEndpoint("https://example.com/v1?target=internal"))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("查询参数");
        assertThatThrownBy(() -> policy.validateConfiguredEndpoint("https://metadata.local/v1"))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("内部网络");
    }

    @Test
    void rejectsDnsAnswersWhenAnyAddressIsNotPublic() throws Exception {
        Dns mixedDns = ignored -> List.of(
            InetAddress.getByName("93.184.216.34"),
            InetAddress.getByName("169.254.169.254")
        );
        CustomLlmEgressPolicy policy = new CustomLlmEgressPolicy(
            false, false, Set.of(443), mixedDns);

        assertThatThrownBy(() -> policy.validateConfiguredEndpoint("https://models.example/v1"))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("不安全");
    }

    @Test
    void rejectsIpv4MappedAndTransitionAddressesThatEncodePrivateTargets() throws Exception {
        CustomLlmEgressPolicy mappedPolicy = new CustomLlmEgressPolicy(
            false, false, Set.of(443), ignored -> List.of(InetAddress.getByName("::ffff:127.0.0.1")));
        CustomLlmEgressPolicy transitionPolicy = new CustomLlmEgressPolicy(
            false, false, Set.of(443), ignored -> List.of(InetAddress.getByName("2002:7f00:1::")));

        assertThatThrownBy(() -> mappedPolicy.validateConfiguredEndpoint("https://models.example/v1"))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("不安全");
        assertThatThrownBy(() -> transitionPolicy.validateConfiguredEndpoint("https://models.example/v1"))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("不安全");
    }

    @Test
    void acceptsPublicHttpsEndpoint() throws Exception {
        Dns publicDns = ignored -> List.of(InetAddress.getByName("93.184.216.34"));
        CustomLlmEgressPolicy policy = new CustomLlmEgressPolicy(
            false, false, Set.of(443), publicDns);

        assertThatCode(() -> policy.validateConfiguredEndpoint("https://models.example/v1"))
            .doesNotThrowAnyException();
    }
}
