package com.interview.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("llm_provider_config")
public class LlmProviderConfig {

    private Long id;
    private String providerKey;
    private String displayName;
    private String baseUrl;
    private String availableModels;
    private Integer enabled;
    private LocalDateTime createdAt;
}
