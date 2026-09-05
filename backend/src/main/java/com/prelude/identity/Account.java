package com.prelude.identity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Authenticated account. Identity owns account, security and profile data
 * only; model execution configuration belongs to the llm module.
 */
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
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
