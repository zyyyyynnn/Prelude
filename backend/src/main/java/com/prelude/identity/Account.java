package com.prelude.identity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("user_account")
public class Account {

    private Long id;
    private String username;
    private String passwordHash;
    private String email;
    private String avatarUrl;
    private String themePreference;
    private Long revision;
    private String lastOperationId;
    private String llmProvider;
    private String llmModel;
    private String llmBaseUrl;
    private String llmApiKeyEncrypted;
    private Integer llmMaxTokens;
    private String llmThinkingDepth;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
