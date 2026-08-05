package com.interview.identity.infrastructure.persistence;

import com.interview.identity.application.port.LegacyAvatarMigrationPort;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

public interface LegacyAvatarMigrationRepository extends LegacyAvatarMigrationPort {

    @Override
    @Select("""
        SELECT `id` AS `user_id`, `avatar_url`
        FROM `user`
        WHERE `avatar_url` LIKE '/uploads/avatars/%'
           OR (
             `avatar_url` LIKE '/media/avatars/%'
             AND LOWER(`avatar_url`) REGEXP '\\.(gif|webp)$'
           )
        ORDER BY `id`
        LIMIT #{limit}
        """)
    List<LegacyAvatarCandidate> findLegacyAvatarBatch(@Param("limit") int limit);

    @Override
    @Update("""
        UPDATE `user`
        SET `avatar_url` = #{replacement}
        WHERE `id` = #{userId} AND `avatar_url` = #{expected}
        """)
    int replaceLegacyAvatarUrl(
        @Param("userId") Long userId,
        @Param("expected") String expected,
        @Param("replacement") String replacement
    );
}
