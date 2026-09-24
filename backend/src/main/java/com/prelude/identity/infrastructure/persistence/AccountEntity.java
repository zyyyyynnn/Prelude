package com.prelude.identity.infrastructure.persistence;

import com.baomidou.mybatisplus.annotation.TableName;
import com.prelude.identity.domain.Account;
import lombok.Data;

/** Row shape of {@code user_account}; keeps the table mapping out of the domain model. */
@Data
@TableName("user_account")
public class AccountEntity {

    private Long id;
    private String username;
    private String passwordHash;
    private String email;
    private String avatarUrl;
    private String themePreference;
    private Long revision;
    private String lastOperationId;

    static AccountEntity of(Account account) {
        AccountEntity entity = new AccountEntity();
        entity.setId(account.getId());
        entity.setUsername(account.getUsername());
        entity.setPasswordHash(account.getPasswordHash());
        entity.setEmail(account.getEmail());
        entity.setAvatarUrl(account.getAvatarUrl());
        entity.setThemePreference(account.getThemePreference());
        entity.setRevision(account.getRevision());
        entity.setLastOperationId(account.getLastOperationId());
        return entity;
    }

    Account toDomain() {
        Account account = new Account();
        account.setId(id);
        account.setUsername(username);
        account.setPasswordHash(passwordHash);
        account.setEmail(email);
        account.setAvatarUrl(avatarUrl);
        account.setThemePreference(themePreference);
        account.setRevision(revision);
        account.setLastOperationId(lastOperationId);
        return account;
    }
}
