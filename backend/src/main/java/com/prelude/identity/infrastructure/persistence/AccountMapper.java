package com.prelude.identity.infrastructure.persistence;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.prelude.identity.api.port.AccountRepository;
import com.prelude.identity.domain.Account;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

public interface AccountMapper extends BaseMapper<AccountEntity>, AccountRepository {

    @Override
    default Account findById(long accountId) {
        AccountEntity entity = selectById(accountId);
        return entity == null ? null : entity.toDomain();
    }

    @Override
    default Account findByUsername(String username) {
        return single(new LambdaQueryWrapper<AccountEntity>().eq(AccountEntity::getUsername, username));
    }

    @Override
    default Account findByEmail(String email) {
        return single(new LambdaQueryWrapper<AccountEntity>().eq(AccountEntity::getEmail, email));
    }

    @Override
    default boolean isUsernameTaken(String username) {
        return selectCount(new LambdaQueryWrapper<AccountEntity>()
            .eq(AccountEntity::getUsername, username)) > 0;
    }

    @Override
    default boolean isUsernameTakenByOther(long accountId, String username) {
        return selectCount(new LambdaQueryWrapper<AccountEntity>()
            .eq(AccountEntity::getUsername, username)
            .ne(AccountEntity::getId, accountId)) > 0;
    }

    @Override
    default void add(Account account) {
        AccountEntity entity = AccountEntity.of(account);
        insert(entity);
        account.setId(entity.getId());
    }

    @Override
    default int replaceProfile(Account account, long expectedRevision, String operationId) {
        return updateProfileGuarded(
            account.getId(),
            account.getUsername(),
            account.getEmail(),
            account.getThemePreference(),
            account.getPasswordHash(),
            account.getAvatarUrl(),
            expectedRevision,
            operationId
        );
    }

    @Override
    default int replaceAvatar(String avatarUrl, Account account, String operationId) {
        return updateProfileGuarded(
            account.getId(),
            account.getUsername(),
            account.getEmail(),
            account.getThemePreference(),
            account.getPasswordHash(),
            avatarUrl,
            account.getRevision(),
            operationId
        );
    }

    private Account single(LambdaQueryWrapper<AccountEntity> query) {
        AccountEntity entity = selectOne(query.last("LIMIT 1"));
        return entity == null ? null : entity.toDomain();
    }

    @Update("""
        UPDATE `user_account`
        SET `username` = #{username},
            `email` = #{email},
            `theme_preference` = #{themePreference},
            `password_hash` = #{passwordHash},
            `avatar_url` = #{avatarUrl},
            `revision` = `revision` + 1,
            `last_operation_id` = #{operationId}
        WHERE `id` = #{accountId} AND `revision` = #{expectedRevision}
        """)
    int updateProfileGuarded(
        @Param("accountId") Long accountId,
        @Param("username") String username,
        @Param("email") String email,
        @Param("themePreference") String themePreference,
        @Param("passwordHash") String passwordHash,
        @Param("avatarUrl") String avatarUrl,
        @Param("expectedRevision") long expectedRevision,
        @Param("operationId") String operationId
    );
}
