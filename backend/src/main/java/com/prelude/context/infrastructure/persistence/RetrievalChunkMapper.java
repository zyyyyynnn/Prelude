package com.prelude.context.infrastructure.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface RetrievalChunkMapper extends BaseMapper<RetrievalChunkEntity> {

    @Select("SELECT COUNT(*) FROM retrieval_scope_tombstone WHERE scope_type = #{scopeType} AND scope_id = #{scopeId}")
    int countTombstones(@Param("scopeType") String scopeType, @Param("scopeId") Long scopeId);

    @Insert("INSERT IGNORE INTO retrieval_scope_tombstone (scope_type, scope_id) VALUES (#{scopeType}, #{scopeId})")
    void insertTombstone(@Param("scopeType") String scopeType, @Param("scopeId") Long scopeId);
}
