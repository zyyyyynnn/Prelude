package com.prelude.identity;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

public interface AccountMapper extends BaseMapper<Account> {

    @Update("""
        UPDATE `user_account`
        SET `llm_provider` = #{providerKey},
            `llm_base_url` = #{baseUrl},
            `llm_model` = #{model},
            `llm_api_key_encrypted` = #{encryptedApiKey},
            `llm_max_tokens` = #{maxTokens},
            `llm_thinking_depth` = #{thinkingDepth}
        WHERE `id` = #{accountId}
        """)
    int updateLlmConfiguration(
        @Param("accountId") Long accountId,
        @Param("providerKey") String providerKey,
        @Param("baseUrl") String baseUrl,
        @Param("model") String model,
        @Param("encryptedApiKey") String encryptedApiKey,
        @Param("maxTokens") Integer maxTokens,
        @Param("thinkingDepth") String thinkingDepth
    );

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
