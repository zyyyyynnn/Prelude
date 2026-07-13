package com.interview.identity.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("user")
public class User {

    private Long id;
    private String username;
    private String password;
    private String email;
    private String avatarUrl;
    private String themePreference;
    private String llmProvider;
    private String llmModel;
    private String llmBaseUrl;
    private String llmApiKeyEncrypted;
    private Integer llmMaxTokens;
    private String llmThinkingDepth;
    private LocalDateTime createdAt;
}
