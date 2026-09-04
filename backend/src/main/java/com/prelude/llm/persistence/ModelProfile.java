package com.prelude.llm.persistence;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("model_profile")
public class ModelProfile {

    private Long id;
    private Long accountId;
    private String provider;
    private String model;
    private Long credentialId;
    private String customEndpointUrl;
    private String reasoningLevel;
    private String effectiveParametersJson;
    private String modelCapabilityJson;
    private String fallbackCapabilitiesJson;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
