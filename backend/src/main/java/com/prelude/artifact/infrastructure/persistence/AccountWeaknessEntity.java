package com.prelude.artifact.infrastructure.persistence;

import com.baomidou.mybatisplus.annotation.TableName;
import com.prelude.artifact.domain.AccountWeakness;
import lombok.Data;

import java.time.LocalDateTime;

/** Row shape of {@code account_weakness}; keeps the table mapping out of the domain model. */
@Data
@TableName("account_weakness")
public class AccountWeaknessEntity {

    private Long id;
    private Long accountId;
    private Long sessionId;
    private String category;
    private String description;
    private LocalDateTime createdAt;

    public static AccountWeaknessEntity of(AccountWeakness weakness) {
        AccountWeaknessEntity entity = new AccountWeaknessEntity();
        entity.setId(weakness.getId());
        entity.setAccountId(weakness.getAccountId());
        entity.setSessionId(weakness.getSessionId());
        entity.setCategory(weakness.getCategory());
        entity.setDescription(weakness.getDescription());
        entity.setCreatedAt(weakness.getCreatedAt());
        return entity;
    }

    public AccountWeakness toDomain() {
        AccountWeakness weakness = new AccountWeakness();
        weakness.setId(id);
        weakness.setAccountId(accountId);
        weakness.setSessionId(sessionId);
        weakness.setCategory(category);
        weakness.setDescription(description);
        weakness.setCreatedAt(createdAt);
        return weakness;
    }
}
