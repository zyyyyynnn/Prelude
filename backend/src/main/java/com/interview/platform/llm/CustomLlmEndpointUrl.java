package com.interview.platform.llm;

import com.interview.shared.api.BusinessException;

import java.net.URI;
import java.util.Arrays;

public final class CustomLlmEndpointUrl {

    private CustomLlmEndpointUrl() {
    }

    public static String normalizeRoot(String input, CustomLlmProtocol protocol) {
        URI uri = parse(input);
        String path = uri.getPath() == null ? "" : trimTrailingSlash(uri.getPath());
        if (path.endsWith(protocol.endpointSuffix())) {
            path = path.substring(0, path.length() - protocol.endpointSuffix().length());
        } else if (Arrays.stream(CustomLlmProtocol.values())
            .map(CustomLlmProtocol::endpointSuffix)
            .anyMatch(path::endsWith)) {
            throw BusinessException.badRequest("Base URL 与所选协议不匹配");
        }
        path = trimTrailingSlash(path);

        try {
            return new URI(
                uri.getScheme().toLowerCase(),
                null,
                uri.getHost(),
                uri.getPort(),
                path.isBlank() ? "" : path,
                null,
                null
            ).toString();
        } catch (Exception exception) {
            throw BusinessException.badRequest("Base URL 格式不正确");
        }
    }

    public static String toInvocationUrl(String root, CustomLlmProtocol protocol) {
        return normalizeRoot(root, protocol) + protocol.endpointSuffix();
    }

    public static String toModelsUrl(String root, CustomLlmProtocol protocol) {
        if (!protocol.supportsModelDiscovery()) {
            throw BusinessException.badRequest("当前协议不支持自动检测模型");
        }
        return normalizeRoot(root, protocol) + "/models";
    }

    private static URI parse(String input) {
        if (input == null || input.isBlank()) {
            throw BusinessException.badRequest("Base URL 不能为空");
        }
        URI uri;
        try {
            uri = URI.create(input.trim());
        } catch (IllegalArgumentException exception) {
            throw BusinessException.badRequest("Base URL 格式不正确");
        }
        String scheme = uri.getScheme();
        if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
            throw BusinessException.badRequest("Base URL 仅支持 http/https");
        }
        if (uri.getHost() == null || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null) {
            throw BusinessException.badRequest("Base URL 不得包含凭证、查询参数或片段");
        }
        return uri;
    }

    private static String trimTrailingSlash(String value) {
        String result = value;
        while (result.endsWith("/") && result.length() > 1) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }
}
