package com.prelude.llm;

import com.prelude.BusinessException;

import java.net.URI;

/**
 * Canonical endpoint-root helpers shared by profile configuration, voice
 * access, and model construction.
 */
final class EndpointRoots {

    private EndpointRoots() {
    }

    static String normalize(String input, String provider) {
        try {
            URI uri = URI.create(input.trim());
            String path = uri.getPath() == null ? "" : trimTrailingSlash(uri.getPath());
            if (CustomLlmProtocol.isCustom(provider)) {
                path = stripEndpointSuffix(path, CustomLlmProtocol.require(provider).endpointSuffix());
            }
            return new URI(uri.getScheme(), null, uri.getHost(), uri.getPort(),
                path.isBlank() ? null : path, null, null).toString();
        } catch (Exception exception) {
            throw BusinessException.badRequest("Base URL 格式不正确");
        }
    }

    static String trimTrailingSlash(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private static String stripEndpointSuffix(String path, String suffix) {
        return path.endsWith(suffix)
            ? trimTrailingSlash(path.substring(0, path.length() - suffix.length()))
            : path;
    }
}
