package com.prelude.llm.persistence;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("provider_credential")
public class ProviderCredential {

    private Long id;
    private Long accountId;
    private String provider;
    private String scopeKey;
    private String apiKeyEncrypted;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static final String SYSTEM_SCOPE = "SYSTEM";
}
