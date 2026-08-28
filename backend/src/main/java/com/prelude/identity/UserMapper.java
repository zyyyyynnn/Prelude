package com.prelude.identity;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.prelude.identity.User;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

public interface UserMapper extends BaseMapper<User> {

    @Update("""
        UPDATE `user`
        SET `llm_provider` = #{providerKey},
            `llm_base_url` = #{baseUrl},
            `llm_model` = #{model},
            `llm_api_key_encrypted` = #{encryptedApiKey},
            `llm_max_tokens` = #{maxTokens},
            `llm_thinking_depth` = #{thinkingDepth}
        WHERE `id` = #{userId}
        """)
    int updateLlmConfiguration(
        @Param("userId") Long userId,
        @Param("providerKey") String providerKey,
        @Param("baseUrl") String baseUrl,
        @Param("model") String model,
        @Param("encryptedApiKey") String encryptedApiKey,
        @Param("maxTokens") Integer maxTokens,
        @Param("thinkingDepth") String thinkingDepth
    );
}
