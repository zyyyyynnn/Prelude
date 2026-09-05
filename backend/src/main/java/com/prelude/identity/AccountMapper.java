package com.prelude.identity;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

public interface AccountMapper extends BaseMapper<Account> {

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
