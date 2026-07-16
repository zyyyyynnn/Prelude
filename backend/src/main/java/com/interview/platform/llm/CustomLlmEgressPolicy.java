package com.interview.platform.llm;

import com.interview.shared.api.BusinessException;
import okhttp3.Dns;
import okhttp3.HttpUrl;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Component
public class CustomLlmEgressPolicy {

    private static final Set<String> INTERNAL_HOST_SUFFIXES = Set.of(
        "localhost", ".localhost", ".local", ".internal", ".home", ".lan"
    );

    private final boolean allowHttp;
    private final boolean allowPrivateAddresses;
    private final Set<Integer> allowedPorts;
    private final Dns delegateDns;

    public CustomLlmEgressPolicy(
        @Value("${prelude.llm.egress.allow-http:false}") boolean allowHttp,
        @Value("${prelude.llm.egress.allow-private-addresses:false}") boolean allowPrivateAddresses,
        @Value("${prelude.llm.egress.allowed-ports:443}") String allowedPorts
    ) {
        this(allowHttp, allowPrivateAddresses, parsePorts(allowedPorts), Dns.SYSTEM);
    }

    CustomLlmEgressPolicy(
        boolean allowHttp,
        boolean allowPrivateAddresses,
        Set<Integer> allowedPorts,
        Dns delegateDns
    ) {
        this.allowHttp = allowHttp;
        this.allowPrivateAddresses = allowPrivateAddresses;
        this.allowedPorts = Set.copyOf(allowedPorts);
        this.delegateDns = delegateDns;
    }

    public void validateConfiguredEndpoint(String endpoint) {
        HttpUrl url;
        try {
            url = HttpUrl.get(endpoint);
        } catch (IllegalArgumentException exception) {
            throw BusinessException.badRequest("Base URL 格式不正确");
        }
        validateUrl(url);
        try {
            guardedLookup(url.host());
        } catch (UnknownHostException exception) {
            throw BusinessException.badRequest("Base URL 域名无法解析或解析结果不安全");
        }
    }

    public void validateUrl(HttpUrl url) {
        String scheme = url.scheme().toLowerCase(Locale.ROOT);
        if (!"https".equals(scheme) && !(allowHttp && "http".equals(scheme))) {
            throw BusinessException.badRequest("Base URL 必须使用 HTTPS");
        }
        if (!url.username().isEmpty() || !url.password().isEmpty()
            || url.query() != null || url.fragment() != null) {
            throw BusinessException.badRequest("Base URL 不得包含凭证、查询参数或片段");
        }
        if (!allowedPorts.contains(url.port())) {
            throw BusinessException.badRequest("Base URL 端口不在允许范围内");
        }
        String host = url.host().toLowerCase(Locale.ROOT);
        if (!allowPrivateAddresses && INTERNAL_HOST_SUFFIXES.stream().anyMatch(host::endsWith)) {
            throw BusinessException.badRequest("Base URL 不得指向本机或内部网络");
        }
    }

    public List<InetAddress> guardedLookup(String hostname) throws UnknownHostException {
        List<InetAddress> addresses = delegateDns.lookup(hostname);
        if (addresses == null || addresses.isEmpty()) {
            throw new UnknownHostException("No addresses returned for " + hostname);
        }
        if (!allowPrivateAddresses && addresses.stream().anyMatch(address -> !isPublicAddress(address))) {
            throw new UnknownHostException("Blocked non-public address for " + hostname);
        }
        return addresses;
    }

    private boolean isPublicAddress(InetAddress address) {
        if (address.isAnyLocalAddress() || address.isLoopbackAddress()
            || address.isLinkLocalAddress() || address.isSiteLocalAddress()
            || address.isMulticastAddress()) {
            return false;
        }
        if (address instanceof Inet4Address) {
            return isPublicIpv4(address.getAddress());
        }
        if (address instanceof Inet6Address) {
            return isPublicIpv6(address.getAddress());
        }
        return false;
    }

    private boolean isPublicIpv4(byte[] bytes) {
        int first = Byte.toUnsignedInt(bytes[0]);
        int second = Byte.toUnsignedInt(bytes[1]);
        int third = Byte.toUnsignedInt(bytes[2]);
        if (first == 0 || first == 10 || first == 127 || first >= 224) return false;
        if (first == 100 && second >= 64 && second <= 127) return false;
        if (first == 169 && second == 254) return false;
        if (first == 172 && second >= 16 && second <= 31) return false;
        if (first == 192 && second == 0 && third == 0) return false;
        if (first == 192 && second == 0 && third == 2) return false;
        if (first == 192 && second == 168) return false;
        if (first == 198 && (second == 18 || second == 19)) return false;
        if (first == 198 && second == 51 && third == 100) return false;
        return !(first == 203 && second == 0 && third == 113);
    }

    private boolean isPublicIpv6(byte[] bytes) {
        int first = Byte.toUnsignedInt(bytes[0]);
        int second = Byte.toUnsignedInt(bytes[1]);
        if (isIpv4Mapped(bytes)) {
            return isPublicIpv4(Arrays.copyOfRange(bytes, 12, 16));
        }
        if (isIpv4Compatible(bytes)) {
            return isPublicIpv4(Arrays.copyOfRange(bytes, 12, 16));
        }
        if (first == 0x20 && second == 0x02) return false;
        if (isNat64WellKnownPrefix(bytes)) return false;
        if ((first & 0xfe) == 0xfc) return false;
        if (first == 0x20 && second == 0x01) {
            int third = Byte.toUnsignedInt(bytes[2]);
            int fourth = Byte.toUnsignedInt(bytes[3]);
            if ((third == 0x00 && fourth == 0x00)
                || (third == 0x0d && fourth == 0xb8)
                || (third == 0x00 && (fourth == 0x02 || fourth == 0x10))) {
                return false;
            }
        }
        return true;
    }

    private boolean isIpv4Mapped(byte[] bytes) {
        for (int index = 0; index < 10; index++) {
            if (bytes[index] != 0) return false;
        }
        return Byte.toUnsignedInt(bytes[10]) == 0xff && Byte.toUnsignedInt(bytes[11]) == 0xff;
    }

    private boolean isIpv4Compatible(byte[] bytes) {
        for (int index = 0; index < 12; index++) {
            if (bytes[index] != 0) return false;
        }
        return true;
    }

    private boolean isNat64WellKnownPrefix(byte[] bytes) {
        if (Byte.toUnsignedInt(bytes[0]) != 0x00 || Byte.toUnsignedInt(bytes[1]) != 0x64
            || Byte.toUnsignedInt(bytes[2]) != 0xff || Byte.toUnsignedInt(bytes[3]) != 0x9b) {
            return false;
        }
        for (int index = 4; index < 12; index++) {
            if (bytes[index] != 0) return false;
        }
        return true;
    }

    private static Set<Integer> parsePorts(String configured) {
        LinkedHashSet<Integer> ports = new LinkedHashSet<>();
        Arrays.stream(configured.split(","))
            .map(String::trim)
            .filter(value -> !value.isEmpty())
            .forEach(value -> {
                try {
                    int port = Integer.parseInt(value);
                    if (port < 1 || port > 65535) {
                        throw new NumberFormatException();
                    }
                    ports.add(port);
                } catch (NumberFormatException exception) {
                    throw new IllegalArgumentException("Invalid prelude.llm.egress.allowed-ports value: " + value);
                }
            });
        if (ports.isEmpty()) {
            throw new IllegalArgumentException("prelude.llm.egress.allowed-ports must not be empty");
        }
        return ports;
    }
}
