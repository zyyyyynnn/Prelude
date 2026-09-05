package com.prelude.llm.persistence;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Immutable frozen execution configuration. Created when a run starts; a
 * later profile mutation never changes an existing snapshot.
 */
@Data
@TableName("model_execution_snapshot")
public class ModelExecutionSnapshot {

    private Long id;
    private Long accountId;
    private Long profileId;
    private String provider;
    private String model;
    private String reasoningLevel;
    private String effectiveParametersJson;
    private String capabilityVersion;
    private String modelCapabilityJson;
    private String fallbackCapabilitiesJson;
    private Long credentialId;
    private String customEndpointUrl;
    private LocalDateTime createdAt;
}
