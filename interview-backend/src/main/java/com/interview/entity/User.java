package com.interview.entity;

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
    private String llmProvider;
    private String llmModel;
    private String llmApiKeyEncrypted;
    private LocalDateTime createdAt;
}
