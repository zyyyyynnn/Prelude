package com.interview.llm;

import com.interview.common.BusinessException;

import java.net.URI;

public final class OpenAiCompatibleUrl {

    private static final String CHAT_COMPLETIONS_SUFFIX = "/chat/completions";

    private OpenAiCompatibleUrl() {
    }

    public static String normalizeRoot(String input) {
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

        String path = uri.getPath() == null ? "" : trimTrailingSlash(uri.getPath());
        if (path.endsWith(CHAT_COMPLETIONS_SUFFIX)) {
            path = path.substring(0, path.length() - CHAT_COMPLETIONS_SUFFIX.length());
        }
        path = trimTrailingSlash(path);

        try {
            return new URI(
                scheme.toLowerCase(),
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

    public static String toModelsUrl(String root) {
        return normalizeRoot(root) + "/models";
    }

    public static String toChatCompletionsUrl(String root) {
        return normalizeRoot(root) + CHAT_COMPLETIONS_SUFFIX;
    }

    private static String trimTrailingSlash(String value) {
        String result = value;
        while (result.endsWith("/") && result.length() > 1) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }
}
