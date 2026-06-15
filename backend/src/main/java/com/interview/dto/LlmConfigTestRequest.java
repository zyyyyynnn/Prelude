package com.interview.dto;

/**
 * 草稿配置测试请求。全部字段可空：无 body 或全空时回退到测试已保存配置。
 */
public record LlmConfigTestRequest(
    String providerKey,

    String baseUrl,

    String model,

    String apiKey,

    Integer maxTokens,

    String thinkingDepth
) {
}
