package com.interview.identity.infrastructure.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.interview.identity.domain.User;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface UserMapper extends BaseMapper<User> {

    @Select("SELECT * FROM `user` WHERE `id` = #{id} FOR UPDATE")
    User selectByIdForUpdate(@Param("id") Long id);
}
