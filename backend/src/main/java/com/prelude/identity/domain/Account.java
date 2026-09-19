package com.prelude.identity.domain;

import lombok.Data;

/**
 * Authenticated account. Identity owns account, security and profile data
 * only; model execution configuration belongs to the llm module.
 */
@Data
public class Account {

    private Long id;
    private String username;
    private String passwordHash;
    private String email;
    private String avatarUrl;
    private String themePreference;
    private Long revision;
    private String lastOperationId;
}
