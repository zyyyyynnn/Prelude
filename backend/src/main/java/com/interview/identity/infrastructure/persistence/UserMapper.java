package com.interview.identity.infrastructure.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.interview.identity.domain.User;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

public interface UserMapper extends BaseMapper<User> {

    @Update("UPDATE `user` SET `username` = #{username} WHERE `id` = #{id}")
    int updateUsername(@Param("id") Long id, @Param("username") String username);

    @Update("UPDATE `user` SET `email` = #{email} WHERE `id` = #{id}")
    int updateEmail(@Param("id") Long id, @Param("email") String email);

    @Update("UPDATE `user` SET `password` = #{password} WHERE `id` = #{id}")
    int updatePassword(@Param("id") Long id, @Param("password") String password);

    @Update("UPDATE `user` SET `theme_preference` = #{themePreference} WHERE `id` = #{id}")
    int updateThemePreference(@Param("id") Long id, @Param("themePreference") String themePreference);

    @Update("UPDATE `user` SET `avatar_revision` = `avatar_revision` + 1 WHERE `id` = #{id}")
    int claimAvatarRevision(@Param("id") Long id);

    @Select("SELECT `avatar_url` FROM `user` WHERE `id` = #{id} FOR UPDATE")
    String selectAvatarUrlForUpdate(@Param("id") Long id);

    @Update("""
        UPDATE `user`
        SET `avatar_url` = #{avatarUrl}
        WHERE `id` = #{id} AND `avatar_revision` = #{avatarRevision}
        """)
    int updateAvatarIfRevision(
        @Param("id") Long id,
        @Param("avatarUrl") String avatarUrl,
        @Param("avatarRevision") Long avatarRevision
    );
}
