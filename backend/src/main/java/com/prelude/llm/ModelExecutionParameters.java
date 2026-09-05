package com.prelude.llm;

import com.prelude.BusinessException;
import tools.jackson.databind.ObjectMapper;

/**
 * Typed execution parameters frozen into every model snapshot. The persisted
 * representation is JSON, but execution never consumes an arbitrary map.
 */
public record ModelExecutionParameters(int maxOutputTokens) {

    public static final int DEFAULT_MAX_OUTPUT_TOKENS = 4096;
    public static final int MIN_MAX_OUTPUT_TOKENS = 1;
    public static final int MAX_MAX_OUTPUT_TOKENS = 32768;

    public static ModelExecutionParameters resolve(Integer requested) {
        int value = requested == null ? DEFAULT_MAX_OUTPUT_TOKENS : requested;
        if (value < MIN_MAX_OUTPUT_TOKENS || value > MAX_MAX_OUTPUT_TOKENS) {
            throw BusinessException.badRequest("最大回复 Token 数必须在 1 到 32768 之间");
        }
        return new ModelExecutionParameters(value);
    }

    public static ModelExecutionParameters fromProfileJson(String json, ObjectMapper objectMapper) {
        if (json == null || json.isBlank()) {
            return resolve(null);
        }
        try {
            var node = objectMapper.readTree(json);
            return resolve(node.path("maxOutputTokens").isMissingNode()
                ? null
                : node.path("maxOutputTokens").asInt());
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw BusinessException.badRequest("模型执行参数无效，请重新保存模型配置");
        }
    }

    public static ModelExecutionParameters fromFrozenJson(String json, ObjectMapper objectMapper) {
        try {
            var node = objectMapper.readTree(json);
            if (node == null || !node.has("maxOutputTokens")) {
                throw new IllegalStateException("Frozen model execution parameters are incomplete");
            }
            int value = node.path("maxOutputTokens").asInt();
            if (value < MIN_MAX_OUTPUT_TOKENS || value > MAX_MAX_OUTPUT_TOKENS) {
                throw new IllegalStateException("Frozen maxOutputTokens is outside the supported range");
            }
            return new ModelExecutionParameters(value);
        } catch (IllegalStateException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("Frozen model execution parameters are invalid", exception);
        }
    }

    public String toJson(ObjectMapper objectMapper) {
        try {
            return objectMapper.writeValueAsString(this);
        } catch (Exception exception) {
            throw new IllegalStateException("Model execution parameters cannot be serialized", exception);
        }
    }
}
